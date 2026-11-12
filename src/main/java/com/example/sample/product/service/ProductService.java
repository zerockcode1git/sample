package com.example.sample.product.service;

import com.example.sample.common.dto.PageResponse;
import com.example.sample.common.exception.InvalidInputException;
import com.example.sample.common.storage.FileStorageService;
import com.example.sample.common.storage.ImageFileValidator;
import com.example.sample.product.dto.ProductCreateRequest;
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

    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    private final ImageFileValidator imageFileValidator;

    /**
     * 상품 등록 + 이미지 업로드.
     * <ol>
     *   <li>상품 정보와 모든 이미지를 먼저 검증 (하나라도 잘못되면 아무것도 올리지 않음)</li>
     *   <li>이미지를 저장소에 올리고 저장 경로를 img1~img3에 넣어 DB에 저장</li>
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

        List<String> uploaded = new ArrayList<>();
        deleteOnRollback(uploaded);
        for (MultipartFile file : files) {
            uploaded.add(fileStorageService.upload(file, IMAGE_DIRECTORY));
        }

        ProductEntity saved = productRepository.save(request.toEntity(uploaded));
        return toResponse(saved);
    }

    public ProductResponse get(Long pno) {
        return toResponse(findOrThrow(pno));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ProductResponse> list(ProductSearchCondition condition, Pageable pageable) {
        ProductSearchCondition cond = condition == null ? new ProductSearchCondition(null, null, null) : condition;
        if (cond.minPrice() != null && cond.maxPrice() != null && cond.minPrice() > cond.maxPrice()) {
            throw new InvalidInputException("최소 가격이 최대 가격보다 클 수 없습니다.");
        }
        return PageResponse.of(productRepository.search(cond, pageable), this::toResponse);
    }

    @Transactional
    public ProductResponse modify(Long pno, ProductUpdateRequest request) {
        if (!StringUtils.hasText(request.pname())) {
            throw new InvalidInputException("상품명은 비어 있을 수 없습니다.");
        }
        ProductEntity product = findOrThrow(pno);
        // 조회 이후 다른 변경이 있었는지 확인
        if (product.getVersion() != request.version()) {
            throw new ProductConflictException(pno);
        }
        product.changePname(request.pname());
        product.changeOnSale(request.onSale());
        product.changeStock(request.stock());

        // 버전 확인과 커밋 사이에 끼어든 변경도 여기서 잡아 비즈니스 예외로 바꿉니다.
        try {
            productRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new ProductConflictException(pno, e);
        }
        return toResponse(product);
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

    @Transactional
    public void remove(Long pno) {
        ProductEntity product = findOrThrow(pno);
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new ProductConflictException(pno, e);
        }
    }

    private ProductResponse toResponse(ProductEntity product) {
        return ProductResponse.from(product, fileStorageService::getUrl);
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
    private void deleteOnRollback(List<String> uploadedPaths) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    uploadedPaths.forEach(fileStorageService::delete);
                }
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
