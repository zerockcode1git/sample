package com.example.sample.product.controller;

import com.example.sample.common.storage.FileStorageService;
import com.example.sample.common.storage.SupabaseStorageProperties;
import com.example.sample.common.storage.ThumbnailGenerator;
import com.example.sample.support.TestImages;
import com.example.sample.product.dto.ProductCreateRequest;
import com.example.sample.product.dto.ProductResponse;
import com.example.sample.product.repository.ProductRepository;
import com.example.sample.product.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 삭제 + 이미지 삭제. 실제 DB + 실제 Supabase Storage.
 * 커밋 후 파일 삭제를 검증하려면 트랜잭션이 실제로 끝나야 하므로 @Transactional을 쓰지 않고 직접 정리합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductDeleteTest {


    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired FileStorageService fileStorageService;
    @Autowired SupabaseStorageProperties properties;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired RestClient supabaseStorageRestClient;

    private final List<ProductResponse> created = new ArrayList<>();

    @BeforeEach
    void requireKey() {
        assumeTrue(StringUtils.hasText(properties.serviceKey()), "supabase.storage.service-key 없음");
    }

    @AfterEach
    void cleanup() {
        for (ProductResponse p : created) {
            productRepository.findById(p.pno()).ifPresent(productRepository::delete);
            fileIds(p).forEach(id -> {
                fileStorageService.delete("products", id);
                fileStorageService.delete("products/thumbs", ThumbnailGenerator.thumbnailId(id));
            });
        }
    }

    @Test
    void deleteProductAndImages() throws Exception {
        ProductResponse p = registerWithImages(2);
        assertThat(fileIds(p)).hasSize(2).allSatisfy(id -> {
            assertThat(storageStatus(id)).isEqualTo(200);
            assertThat(thumbnailStatus(id)).isEqualTo(200);
        });

        mvc.perform(delete("/api/products/{pno}", p.pno())).andExpect(status().isNoContent());

        assertThat(productRepository.findById(p.pno())).isEmpty();
        assertThat(fileIds(p)).allSatisfy(id -> {
            assertThat(storageStatus(id)).isNotEqualTo(200);   // 원본 삭제
            assertThat(thumbnailStatus(id)).isNotEqualTo(200); // 썸네일 삭제
        });
    }

    @Test
    void deleteProductWithoutImages() throws Exception {
        ProductResponse p = registerWithImages(0);
        mvc.perform(delete("/api/products/{pno}", p.pno())).andExpect(status().isNoContent());
        assertThat(productRepository.findById(p.pno())).isEmpty();
    }

    // DB 삭제가 롤백되면 이미지는 지우지 않음
    @Test
    void keepImagesWhenDbDeleteRollsBack() {
        ProductResponse p = registerWithImages(1);

        transactionTemplate.executeWithoutResult(tx -> {
            productService.remove(p.pno());
            tx.setRollbackOnly();
        });

        assertThat(productRepository.findById(p.pno())).isPresent();
        assertThat(storageStatus(fileIds(p).getFirst())).isEqualTo(200);
        assertThat(thumbnailStatus(fileIds(p).getFirst())).isEqualTo(200);
    }

    @Test
    void deleteNotFound() throws Exception {
        mvc.perform(delete("/api/products/{pno}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    private ProductResponse registerWithImages(int count) {
        List<org.springframework.web.multipart.MultipartFile> images = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            images.add(TestImages.pngPart("삭제테스트" + i + ".png"));
        }
        ProductResponse p = productService.register(
                new ProductCreateRequest("삭제테스트상품", "삭제 테스트", 1000, true, 1), images);
        created.add(p);
        return p;
    }

    // 응답의 upload URL에서 파일 ID({uuid}.{ext})만 추출
    private static List<String> fileIds(ProductResponse p) {
        return Stream.of(p.upload1(), p.upload2(), p.upload3())
                .filter(StringUtils::hasText)
                .map(url -> url.substring(url.lastIndexOf('/') + 1))
                .toList();
    }

    // Storage에 파일이 있으면 200. Public URL은 CDN 캐시 때문에 삭제 직후에도 200일 수 있어 인증 API로 확인합니다.
    private int storageStatus(String fileId) {
        return keyStatus("products/" + fileId);
    }

    private int thumbnailStatus(String fileId) {
        return keyStatus("products/thumbs/" + ThumbnailGenerator.thumbnailId(fileId));
    }

    private int keyStatus(String key) {
        return supabaseStorageRestClient.get()
                .uri("/object/authenticated/" + properties.bucket() + "/" + key)
                .exchange((req, res) -> res.getStatusCode().value());
    }
}
