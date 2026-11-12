package com.example.sample.product.service;

import com.example.sample.common.dto.PageResponse;
import com.example.sample.common.exception.InvalidInputException;
import com.example.sample.product.dto.ProductCreateRequest;
import com.example.sample.product.dto.ProductResponse;
import com.example.sample.product.dto.ProductSearchCondition;
import com.example.sample.product.dto.ProductUpdateRequest;
import com.example.sample.product.exception.InsufficientStockException;
import com.example.sample.product.exception.InvalidStockException;
import com.example.sample.product.exception.ProductConflictException;
import com.example.sample.product.exception.ProductNotFoundException;
import com.example.sample.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 서비스의 트랜잭션/잠금 동작을 그대로 검증하기 위해 테스트에 @Transactional을 붙이지 않고, 만든 데이터는 직접 지웁니다.
@SpringBootTest
class ProductServiceTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        productRepository.deleteAllById(created);
    }

    private ProductResponse register(int stock) {
        ProductResponse p = productService.register(
                new ProductCreateRequest("서비스테스트상품", "설명", 10_000, true, stock), List.of());
        created.add(p.pno());
        return p;
    }

    @Test
    void registerAndGet() {
        ProductResponse saved = register(5);

        ProductResponse found = productService.get(saved.pno());

        assertThat(found.pname()).isEqualTo("서비스테스트상품");
        assertThat(found.stock()).isEqualTo(5);
        assertThat(found.version()).isZero();
    }

    @Test
    void getNotFound() {
        assertThatThrownBy(() -> productService.get(-1L)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void registerNegativeStock() {
        assertThatThrownBy(() -> register(-1)).isInstanceOf(InvalidStockException.class);
    }

    @Test
    void modify() {
        ProductResponse saved = register(5);

        ProductResponse updated = productService.modify(saved.pno(),
                new ProductUpdateRequest("수정됨", false, 7, saved.version()));

        assertThat(updated.pname()).isEqualTo("수정됨");
        assertThat(updated.onSale()).isFalse();
        assertThat(updated.stock()).isEqualTo(7);
        assertThat(updated.version()).isEqualTo(saved.version() + 1);
    }

    @Test
    void modifyWithStaleVersion() {
        ProductResponse viewed = register(5);
        // 조회 이후 다른 사용자가 먼저 수정
        productService.modify(viewed.pno(), new ProductUpdateRequest("먼저수정", true, 5, viewed.version()));

        assertThatThrownBy(() -> productService.modify(viewed.pno(),
                new ProductUpdateRequest("나중수정", true, 5, viewed.version())))
                .isInstanceOf(ProductConflictException.class);
        assertThat(productService.get(viewed.pno()).pname()).isEqualTo("먼저수정");
    }

    @Test
    void modifyNegativeStock() {
        ProductResponse saved = register(5);
        assertThatThrownBy(() -> productService.modify(saved.pno(),
                new ProductUpdateRequest("상품", true, -1, saved.version())))
                .isInstanceOf(InvalidStockException.class);
    }

    @Test
    void changeStockInsufficient() {
        ProductResponse saved = register(3);

        assertThatThrownBy(() -> productService.changeStock(saved.pno(), -5))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(productService.get(saved.pno()).stock()).isEqualTo(3); // 변경 없음
    }

    @Test
    void changeStockNotFound() {
        assertThatThrownBy(() -> productService.changeStock(-1L, 1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void concurrentStockDecrease() throws Exception {
        int stock = 5;
        int threads = 8;
        ProductResponse saved = register(stock);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    productService.changeStock(saved.pno(), -1);
                    success.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficient.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // 동시에 8번 차감해도 재고 5개만큼만 성공하고 나머지는 재고 부족으로 거부
        assertThat(success.get()).isEqualTo(stock);
        assertThat(insufficient.get()).isEqualTo(threads - stock);
        assertThat(productService.get(saved.pno()).stock()).isZero();
    }

    @Test
    void listWithInvalidPriceRange() {
        assertThatThrownBy(() -> productService.list(new ProductSearchCondition(null, 100, 10), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void list() {
        PageResponse<ProductResponse> page = productService.list(
                new ProductSearchCondition("무선", null, null), PageRequest.of(0, 10));
        assertThat(page.content()).isNotEmpty().allMatch(p ->
                p.pname().contains("무선") || p.pdesc().contains("무선"));
    }

    @Test
    void remove() {
        ProductResponse saved = register(1);
        productService.remove(saved.pno());
        assertThatThrownBy(() -> productService.get(saved.pno())).isInstanceOf(ProductNotFoundException.class);
    }
}
