package com.example.sample.product.repository;

import com.example.sample.product.entity.ProductEntity;
import com.example.sample.product.repository.search.ProductSearch;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 엔티티 관리. insert/update(save), delete, select(findById)는 JpaRepository가 제공하고,
 * 검색 + 페이징 목록은 {@link ProductSearch#search}가 담당합니다.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, ProductSearch {
}
