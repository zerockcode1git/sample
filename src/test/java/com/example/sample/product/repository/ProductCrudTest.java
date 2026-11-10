package com.example.sample.product.repository;

import com.example.sample.product.entity.ProductEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductCrudTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager em;

    @Test
    void insert() {
        ProductEntity saved = productRepository.save(newProduct());

        assertThat(saved.getPno()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void select() {
        Long pno = productRepository.save(newProduct()).getPno();
        em.flush();
        em.clear();

        ProductEntity found = productRepository.findById(pno).orElseThrow();

        assertThat(found.getPname()).isEqualTo("CRUD테스트상품");
        assertThat(found.getPrice()).isEqualTo(10000);
    }

    @Test
    void update() {
        Long pno = productRepository.save(newProduct()).getPno();

        ProductEntity product = productRepository.findById(pno).orElseThrow();
        product.changePname("수정된상품");
        product.changeOnSale(false);
        product.changeStock(0);
        em.flush();
        em.clear();

        ProductEntity updated = productRepository.findById(pno).orElseThrow();
        assertThat(updated.getPname()).isEqualTo("수정된상품");
        assertThat(updated.isOnSale()).isFalse();
        assertThat(updated.getStock()).isZero();
    }

    @Test
    void delete() {
        Long pno = productRepository.save(newProduct()).getPno();

        productRepository.deleteById(pno);
        em.flush();
        em.clear();

        assertThat(productRepository.findById(pno)).isEmpty();
    }

    private ProductEntity newProduct() {
        return ProductEntity.builder()
                .pname("CRUD테스트상품").pdesc("설명").price(10000).onSale(true).stock(5)
                .build();
    }
}
