package com.example.sample.product.service;

import com.example.sample.common.dto.PageResponse;
import com.example.sample.common.exception.InvalidInputException;
import com.example.sample.common.storage.FileStorageService;
import com.example.sample.common.storage.ImageFileValidator;
import com.example.sample.common.storage.InvalidFileException;
import com.example.sample.common.storage.ThumbnailGenerator;
import com.example.sample.product.dto.ProductCreateRequest;
import com.example.sample.product.dto.ProductImage;
import com.example.sample.product.dto.ProductListResponse;
import com.example.sample.product.dto.ProductResponse;
import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.dto.ProductUpdateRequest;
import com.example.sample.product.entity.ProductEntity;
import com.example.sample.product.exception.InvalidStockException;
import com.example.sample.product.exception.ProductConflictException;
import com.example.sample.product.exception.ProductNotFoundException;
import com.example.sample.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 상품 관련 비즈니스 로직.
 *
 * <h3>동시성 처리</h3>
 * <ul>
 *   <li>재고 증감({@link #changeStock}): 비관적 잠금(SELECT FOR UPDATE)으로 같은 상품에 대한 요청을 줄 세웁니다.
 *       앞선 변경이 반영된 값에서 계산하므로 동시 차감에도 재고가 맞고, 음수가 되면 예외로 거부합니다.</li>
 *   <li>상품 수정({@link #modify}): 낙관적 잠금(@Version). 클라이언트가 조회한 버전과 현재 버전이 다르면
 *       (조회 이후 누군가 변경했다면) 덮어쓰지 않고 {@link ProductConflictException}을 던집니다.</li>
 *   <li>목록 조회({@link #list}): REPEATABLE_READ로 목록과 전체 건수를 같은 스냅샷에서 읽습니다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private static final int MAX_IMAGES = 3;
    private static final String IMAGE_DIRECTORY = "products";
    /** 썸네일: products/thumbs/{uuid}.jpg (원본 파일 ID에서 계산하므로 DB 컬럼 없음) */
    private static final String THUMBNAIL_DIRECTORY = "products/thumbs";

    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    private final ImageFileValidator imageFileValidator;
    private final ThumbnailGenerator thumbnailGenerator;

    /**
     * 상품 등록 + 이미지 업로드.
     * <ol>
     *   <li>상품 정보와 모든 이미지를 먼저 검증 (하나라도 잘못되면 아무것도 올리지 않음)</li>
     *   <li>이미지를 저장소(products/{uuid}.{확장자})에 올리고 원본 이름은 img1~img3, 파일 ID({uuid}.{확장자})는 upload1~upload3에 넣어 DB에 저장</li>
     *   <li>트랜잭션이 커밋되지 않으면(DB 저장 실패, 업로드 도중 실패 등) 이미 올린 이미지를 삭제</li>
     * </ol>
     *
     * @param images 0~3개. null 가능
     */
    @Transactional
    public ProductResponse register(ProductCreateRequest request, List<MultipartFile> images) {
        validateCreate(request);
        List<MultipartFile> files = selectedFiles(images);
        imageFileValidator.validate(files, MAX_IMAGES);
        List<byte[]> thumbnails = createThumbnails(files);

        List<ProductImage> productImages = uploadImages(files, thumbnails);
        ProductEntity saved = productRepository.save(request.toEntity(productImages));
        return toResponse(saved);
    }

    public ProductResponse get(Long pno) {
        return toResponse(findOrThrow(pno));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ProductListResponse> list(ProductSearchCondition condition, Pageable pageable) {
        ProductSearchCondition cond = condition == null ? new ProductSearchCondition(null, null, null) : condition;
        if (cond.minPrice() != null && cond.maxPrice() != null && cond.minPrice() > cond.maxPrice()) {
            throw new InvalidInputException("최소 가격이 최대 가격보다 클 수 없습니다.");
        }
        return PageResponse.of(productRepository.search(cond, pageable),
                p -> ProductListResponse.from(p, fileId -> fileStorageService.getUrl(THUMBNAIL_DIRECTORY,
                        ThumbnailGenerator.thumbnailId(fileId))));
    }

    /** 이미지는 그대로 두고 상품 정보만 수정 */
    @Transactional
    public ProductResponse modify(Long pno, ProductUpdateRequest request) {
        return modify(pno, request, List.of());
    }

    /**
     * 상품 수정 + 이미지 교체.
     * <ol>
     *   <li>입력, 버전, 유지할 이미지(keepImages), 새 이미지를 모두 검증 (실패하면 아무것도 올리지 않음)</li>
     *   <li>새 이미지 업로드</li>
     *   <li>DB 수정: 최종 이미지 = 유지한 기존 이미지 + 새 이미지 (최대 3개)</li>
     *   <li>커밋 후: 유지하지 않은 기존 이미지 삭제 / 롤백 시: 새로 올린 이미지 삭제 (기존 이미지는 그대로)</li>
     * </ol>
     *
     * @param images 새 이미지 0~3개. null 가능
     */
    @Transactional
    public ProductResponse modify(Long pno, ProductUpdateRequest request, List<MultipartFile> images) {
        if (!StringUtils.hasText(request.pname())) {
            throw new InvalidInputException("상품명은 비어 있을 수 없습니다.");
        }
        if (request.stock() < 0) {
            throw new InvalidStockException(request.stock());
        }
        ProductEntity product = findOrThrow(pno);
        // 조회 이후 다른 변경이 있었는지 확인 (업로드 전에 거부)
        if (product.getVersion() != request.version()) {
            throw new ProductConflictException(pno);
        }

        List<ProductImage> current = product.getImages();
        List<ProductImage> kept = keptImages(current, request.keepImages());
        List<MultipartFile> files = selectedFiles(images);
        if (kept.size() + files.size() > MAX_IMAGES) {
            throw new InvalidFileException("이미지는 최대 %d개까지 가능합니다. 유지 %d개 + 새 이미지 %d개"
                    .formatted(MAX_IMAGES, kept.size(), files.size()));
        }
        imageFileValidator.validate(files, MAX_IMAGES);
        List<byte[]> thumbnails = createThumbnails(files);

        // 1) 새 이미지 + 썸네일 업로드 (롤백되면 삭제)
        List<ProductImage> finalImages = new ArrayList<>(kept);
        finalImages.addAll(uploadImages(files, thumbnails));

        // 2) DB 수정
        product.changePname(request.pname());
        product.changeOnSale(request.onSale());
        product.changeStock(request.stock());
        product.changeImages(finalImages);
        // 버전 확인과 커밋 사이에 끼어든 변경도 여기서 잡아 비즈니스 예외로 바꿉니다.
        try {
            productRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new ProductConflictException(pno, e);
        }

        // 3) 커밋 후 유지하지 않은 기존 이미지 삭제
        deleteAfterCommit(current.stream().filter(img -> !kept.contains(img)).map(ProductImage::fileId).toList());
        return toResponse(product);
    }

    /**
     * 유지할 기존 이미지를 요청 순서대로 고릅니다. null이면 전부 유지.
     * 값은 파일 ID 또는 URL(마지막 경로만 사용). 현재 상품의 이미지가 아니거나 중복이면 400.
     */
    private static List<ProductImage> keptImages(List<ProductImage> current, List<String> keepImages) {
        if (keepImages == null) {
            return current;
        }
        List<ProductImage> kept = new ArrayList<>();
        for (String value : keepImages) {
            String fileId = value == null ? "" : value.substring(value.lastIndexOf('/') + 1).trim();
            ProductImage image = current.stream().filter(img -> img.fileId().equals(fileId)).findFirst()
                    .orElseThrow(() -> new InvalidInputException("이 상품의 이미지가 아닙니다. keepImages=" + value));
            if (kept.contains(image)) {
                throw new InvalidInputException("keepImages에 같은 이미지가 중복되었습니다. " + value);
            }
            kept.add(image);
        }
        return kept;
    }

    /**
     * 재고 증감.
     *
     * @param amount 양수면 입고, 음수면 출고(차감)
     */
    @Transactional
    public ProductResponse changeStock(Long pno, int amount) {
        if (amount == 0) {
            throw new InvalidInputException("재고 증감 수량은 0일 수 없습니다.");
        }
        ProductEntity product;
        try {
            product = productRepository.findByIdForUpdate(pno).orElseThrow(() -> new ProductNotFoundException(pno));
        } catch (PessimisticLockingFailureException e) {
            throw new ProductConflictException(pno, e);
        }
        product.adjustStock(amount);
        productRepository.flush();
        return toResponse(product);
    }

    /**
     * 상품 삭제 + 업로드된 이미지 삭제.
     * DB 삭제를 먼저 처리하고, 트랜잭션이 <b>커밋된 뒤에만</b> 저장소의 이미지(upload1~3)를 지웁니다.
     * DB 삭제가 실패(롤백)하면 이미지는 그대로 남으므로 상품과 이미지가 어긋나지 않습니다.
     * 이미지 삭제가 실패해도 상품 삭제는 이미 확정되었으므로 로그만 남깁니다.
     */
    @Transactional
    public void remove(Long pno) {
        ProductEntity product = findOrThrow(pno);
        List<String> fileIds = Stream.of(product.getUpload1(), product.getUpload2(), product.getUpload3())
                .filter(StringUtils::hasText)
                .toList();
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new ProductConflictException(pno, e);
        }
        deleteAfterCommit(fileIds);
    }

    private ProductResponse toResponse(ProductEntity product) {
        return ProductResponse.from(product, fileId -> fileStorageService.getUrl(IMAGE_DIRECTORY, fileId));
    }

    // 모든 파일을 디코딩해 썸네일을 미리 만듭니다. 이미지가 아니면 여기서 거부되어 아무것도 올라가지 않습니다.
    private List<byte[]> createThumbnails(List<MultipartFile> files) {
        return files.stream().map(thumbnailGenerator::create).toList();
    }

    // 원본과 썸네일을 업로드합니다. 트랜잭션이 커밋되지 않으면 올린 파일을 모두 지웁니다.
    private List<ProductImage> uploadImages(List<MultipartFile> files, List<byte[]> thumbnails) {
        List<String> uploaded = new ArrayList<>();
        deleteOnRollback(uploaded);
        List<ProductImage> result = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String fileId = fileStorageService.upload(file, IMAGE_DIRECTORY);
            uploaded.add(fileId); // 썸네일 업로드가 실패해도 원본은 롤백 때 지워지도록 먼저 기록
            fileStorageService.upload(thumbnails.get(i), ThumbnailGenerator.CONTENT_TYPE, THUMBNAIL_DIRECTORY,
                    ThumbnailGenerator.thumbnailId(fileId));
            result.add(new ProductImage(originalName(file), fileId));
        }
        return result;
    }

    // 원본과 썸네일을 함께 삭제
    private void deleteImageFiles(String fileId) {
        fileStorageService.delete(IMAGE_DIRECTORY, fileId);
        fileStorageService.delete(THUMBNAIL_DIRECTORY, ThumbnailGenerator.thumbnailId(fileId));
    }

    // 원본 파일 이름. 일부 브라우저가 붙이는 경로(C:\\...\\a.png)는 떼고, 컬럼 길이(500)에 맞춥니다.
    private static String originalName(MultipartFile file) {
        String name = StringUtils.getFilename(String.valueOf(file.getOriginalFilename()).replace('\\', '/'));
        return name.length() > 500 ? name.substring(name.length() - 500) : name;
    }

    // 브라우저 폼에서 파일을 고르지 않으면 이름 없는 빈 파트가 오므로 제외합니다.
    private static List<MultipartFile> selectedFiles(List<MultipartFile> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .filter(f -> !(f.isEmpty() && !StringUtils.hasText(f.getOriginalFilename())))
                .toList();
    }

    // 커밋되지 않고 끝나면 업로드한 파일을 지워 저장소에 고아 파일이 남지 않게 합니다.
    private void deleteOnRollback(List<String> uploadedFileIds) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    uploadedFileIds.forEach(ProductService.this::deleteImageFiles);
                }
            }
        });
    }

    // 커밋이 끝난 뒤에 파일을 지웁니다. 롤백되면 호출되지 않습니다.
    private void deleteAfterCommit(List<String> fileIds) {
        if (fileIds.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileIds.forEach(ProductService.this::deleteImageFiles);
            }
        });
    }

    private ProductEntity findOrThrow(Long pno) {
        return productRepository.findById(pno).orElseThrow(() -> new ProductNotFoundException(pno));
    }

    private void validateCreate(ProductCreateRequest request) {
        if (!StringUtils.hasText(request.pname())) {
            throw new InvalidInputException("상품명은 비어 있을 수 없습니다.");
        }
        if (request.price() < 0) {
            throw new InvalidInputException("상품 가격은 0 이상이어야 합니다.");
        }
        // 엔티티 생성 시에도 검사하지만, 이미지를 올리기 전에 미리 거부합니다.
        if (request.stock() < 0) {
            throw new InvalidStockException(request.stock());
        }
    }
}
