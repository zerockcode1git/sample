package com.example.sample.product.service;

import com.example.sample.common.dto.PageResponse;
import com.example.sample.common.exception.InvalidInputException;
import com.example.sample.product.dto.ProductCreateRequest;
import com.example.sample.product.dto.ProductResponse;
import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.dto.ProductUpdateRequest;
import com.example.sample.product.entity.ProductEntity;
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
import org.springframework.util.StringUtils;

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

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse register(ProductCreateRequest request) {
        validateCreate(request);
        ProductEntity saved = productRepository.save(request.toEntity());
        return ProductResponse.from(saved);
    }

    public ProductResponse get(Long pno) {
        return ProductResponse.from(findOrThrow(pno));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ProductResponse> list(ProductSearchCondition condition, Pageable pageable) {
        ProductSearchCondition cond = condition == null ? new ProductSearchCondition(null, null, null) : condition;
        if (cond.minPrice() != null && cond.maxPrice() != null && cond.minPrice() > cond.maxPrice()) {
            throw new InvalidInputException("최소 가격이 최대 가격보다 클 수 없습니다.");
        }
        return PageResponse.of(productRepository.search(cond, pageable), ProductResponse::from);
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
        return ProductResponse.from(product);
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
        return ProductResponse.from(product);
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
    }
}
