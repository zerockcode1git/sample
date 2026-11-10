package com.example.sample.product.repository;

import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.entity.ProductEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 DB(local 프로필)에 붙어서 실행하고, 각 테스트는 롤백됩니다.
@SpringBootTest
@Transactional
class ProductRepositoryTest {

    @Autowired ProductRepository productRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbcTemplate;

    private ProductEntity saved;

    @BeforeEach
    void setUp() {
        saved = productRepository.save(product("테스트노트북XYZ", "가벼운 울트라북", 1_500_000));
        productRepository.save(product("테스트마우스XYZ", "무선 노트북용 마우스", 30_000));
        productRepository.save(product("테스트키보드XYZ", "기계식 키보드", 120_000));
        em.flush();
        em.clear();
    }

    @Test
    void insertAndSelect() {
        ProductEntity found = productRepository.findById(saved.getPno()).orElseThrow();
        assertThat(found.getPname()).isEqualTo("테스트노트북XYZ");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void update() {
        ProductEntity found = productRepository.findById(saved.getPno()).orElseThrow();
        found.changePname("변경된이름");
        found.changeOnSale(false);
        found.changeStock(0);
        em.flush();
        em.clear();

        ProductEntity updated = productRepository.findById(saved.getPno()).orElseThrow();
        assertThat(updated.getPname()).isEqualTo("변경된이름");
        assertThat(updated.isOnSale()).isFalse();
        assertThat(updated.getStock()).isZero();
    }

    @Test
    void delete() {
        productRepository.deleteById(saved.getPno());
        em.flush();
        assertThat(productRepository.findById(saved.getPno())).isEmpty();
    }

    @Test
    void searchByKeywordInNameOrDesc() {
        // "노트북"은 상품명(노트북)과 설명(노트북용 마우스) 양쪽에 걸림
        Page<ProductEntity> page = productRepository.search(
                new ProductSearchCondition("노트북", null, null), PageRequest.of(0, 10));
        List<String> names = page.getContent().stream().map(ProductEntity::getPname).toList();
        assertThat(names).contains("테스트노트북XYZ", "테스트마우스XYZ").doesNotContain("테스트키보드XYZ");
    }

    @Test
    void searchByKeywordAndPriceRange() {
        Page<ProductEntity> page = productRepository.search(
                new ProductSearchCondition("xyz", 100_000, 200_000), PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(ProductEntity::getPname).containsExactly("테스트키보드XYZ");
    }

    @Test
    void searchPaging() {
        Page<ProductEntity> page = productRepository.search(
                new ProductSearchCondition("테스트", null, null),
                PageRequest.of(0, 2, Sort.by("price").ascending()));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getPrice()).isLessThanOrEqualTo(page.getContent().get(1).getPrice());
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void trigramIndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'tbl_product'", String.class);
        assertThat(indexes).contains("idx_product_pname_trgm", "idx_product_pdesc_trgm", "idx_product_price");
    }

    private ProductEntity product(String name, String desc, int price) {
        return ProductEntity.builder()
                .pname(name).pdesc(desc).price(price).onSale(true).stock(10)
                .build();
    }
}
