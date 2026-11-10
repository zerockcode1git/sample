package com.example.sample.product.repository;

import com.example.sample.product.entity.ProductEntity;
import com.example.sample.product.repository.search.ProductSearch;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 상품 엔티티 관리. insert/update(save), delete, select(findById)는 JpaRepository가 제공하고,
 * 검색 + 페이징 목록은 {@link ProductSearch#search}가 담당합니다.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, ProductSearch {

    /**
     * 재고 증감용 비관적 잠금 조회 (SELECT ... FOR UPDATE).
     * 같은 상품을 동시에 변경하려는 트랜잭션은 앞 트랜잭션이 끝날 때까지 대기하며, 3초가 지나면 실패합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from ProductEntity p where p.pno = :pno")
    Optional<ProductEntity> findByIdForUpdate(@Param("pno") Long pno);
}
