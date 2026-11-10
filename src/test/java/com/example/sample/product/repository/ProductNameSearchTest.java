package com.example.sample.product.repository;

import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.entity.ProductEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductNameSearchTest {

    @Autowired
    ProductRepository productRepository;

    @Test
    void searchByProductName() {
        productRepository.save(ProductEntity.builder()
                .pname("검색테스트상품").price(1000).onSale(true).stock(1).build());

        Page<ProductEntity> result = productRepository.search(
                new ProductSearchCondition("검색테스트", null, null), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ProductEntity::getPname).contains("검색테스트상품");
    }
}
