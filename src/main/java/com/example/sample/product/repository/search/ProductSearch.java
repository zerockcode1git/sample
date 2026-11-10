package com.example.sample.product.repository.search;

import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductSearch {

    Page<ProductEntity> search(ProductSearchCondition condition, Pageable pageable);
}
