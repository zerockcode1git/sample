package com.example.sample.product.controller;

import com.example.sample.common.storage.FileStorageService;
import com.example.sample.common.storage.SupabaseStorageProperties;
import com.example.sample.common.storage.ThumbnailGenerator;
import com.example.sample.support.TestImages;
import com.example.sample.product.dto.ProductCreateRequest;
import com.example.sample.product.dto.ProductImage;
import com.example.sample.product.dto.ProductResponse;
import com.example.sample.product.entity.ProductEntity;
import com.example.sample.product.repository.ProductRepository;
import com.example.sample.product.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.example.sample.product.controller.ProductControllerTest.productPart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 수정 + 이미지 교체. 실제 DB + 실제 Supabase Storage.
 * 커밋/롤백 후 파일 처리를 검증하려면 트랜잭션이 실제로 끝나야 하므로 @Transactional을 쓰지 않고 직접 정리합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductModifyTest {


    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired FileStorageService fileStorageService;
    @Autowired SupabaseStorageProperties properties;
    @Autowired RestClient supabaseStorageRestClient;

    private final List<Long> createdProducts = new ArrayList<>();
    private final Set<String> touchedFiles = new HashSet<>();

    @BeforeEach
    void requireKey() {
        assumeTrue(StringUtils.hasText(properties.serviceKey()), "supabase.storage.service-key 없음");
    }

    @AfterEach
    void cleanup() {
        for (Long pno : createdProducts) {
            productRepository.findById(pno).ifPresent(p -> {
                p.getImages().forEach(img -> touchedFiles.add(img.fileId()));
                productRepository.delete(p);
            });
        }
        touchedFiles.forEach(id -> {
            fileStorageService.delete("products", id);
            fileStorageService.delete("products/thumbs", ThumbnailGenerator.thumbnailId(id));
        });
    }

    // 기존 A, B 중 B만 유지 + 새 C 추가 → [B, C], A는 Storage에서 삭제
    @Test
    void keepSomeAndAddNew() throws Exception {
        ProductEntity before = registerWithImages("A.png", "B.png");
        String a = before.getUpload1(), b = before.getUpload2();

        mvc.perform(modify(before, "[\"%s\"]".formatted(fileStorageService.getUrl("products", b))) // URL로 지정
                        .file(image("새 이미지 C.png")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pname").value("수정된상품"))
                .andExpect(jsonPath("$.img1").value("B.png"))
                .andExpect(jsonPath("$.upload1").value(fileStorageService.getUrl("products", b)))
                .andExpect(jsonPath("$.img2").value("새 이미지 C.png"))
                .andExpect(jsonPath("$.img3").isEmpty())
                .andExpect(jsonPath("$.version").value(before.getVersion() + 1));

        ProductEntity after = reload(before);
        String c = after.getUpload2();
        assertThat(after.getImages()).extracting(ProductImage::originalName).containsExactly("B.png", "새 이미지 C.png");
        assertThat(exists(a)).isFalse();   // 유지하지 않은 기존 이미지 삭제
        assertThat(thumbExists(a)).isFalse();
        assertThat(exists(b)).isTrue();    // 유지
        assertThat(thumbExists(b)).isTrue();
        assertThat(exists(c)).isTrue();    // 새로 업로드 (썸네일 포함)
        assertThat(thumbExists(c)).isTrue();
    }

    // keepImages 생략: 기존 이미지 모두 유지, 순서 변경도 가능
    @Test
    void omitKeepImagesKeepsAllAndReorder() throws Exception {
        ProductEntity before = registerWithImages("A.png", "B.png");
        String a = before.getUpload1(), b = before.getUpload2();

        mvc.perform(modify(before, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.img1").value("A.png"))
                .andExpect(jsonPath("$.img2").value("B.png"));

        ProductEntity mid = reload(before);
        mvc.perform(modify(mid, "[\"%s\", \"%s\"]".formatted(b, a))).andExpect(status().isOk()) // 파일 ID로 지정
                .andExpect(jsonPath("$.img1").value("B.png"))
                .andExpect(jsonPath("$.img2").value("A.png"));
        assertThat(exists(a)).isTrue();
        assertThat(exists(b)).isTrue();
    }

    // keepImages = [] → 기존 이미지 모두 삭제
    @Test
    void removeAllImages() throws Exception {
        ProductEntity before = registerWithImages("A.png");
        mvc.perform(modify(before, "[]")).andExpect(status().isOk())
                .andExpect(jsonPath("$.img1").isEmpty())
                .andExpect(jsonPath("$.upload1").isEmpty());
        assertThat(exists(before.getUpload1())).isFalse();
    }

    // 검증 실패는 업로드 전에 거부: 이미지 변화 없음
    @Test
    void rejectBeforeUpload() throws Exception {
        ProductEntity before = registerWithImages("A.png", "B.png");
        List<String> filesBefore = productFiles();

        // 다른 상품의 이미지
        mvc.perform(modify(before, "[\"not-mine.png\"]").file(image("C.png")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이 상품의 이미지가 아닙니다. keepImages=not-mine.png"));
        // 유지 2 + 새 2 = 4개
        mvc.perform(modify(before, null).file(image("C.png")).file(image("D.png")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미지는 최대 3개까지 가능합니다. 유지 2개 + 새 이미지 2개"));
        // 버전 불일치
        mvc.perform(multipart(HttpMethod.PUT, "/api/products/{pno}", before.getPno())
                        .file(productPart("""
                                {"pname":"x","onSale":true,"stock":1,"version":99}
                                """)).file(image("C.png")))
                .andExpect(status().isConflict());

        assertThat(productFiles()).isEqualTo(filesBefore);
        assertThat(reload(before).getImages()).isEqualTo(before.getImages());
    }

    // 업로드 후 DB 수정 실패(상품명 200자 초과) → 새 이미지만 삭제, 기존 이미지와 DB는 그대로
    @Test
    void rollbackDeletesOnlyNewImages() throws Exception {
        ProductEntity before = registerWithImages("A.png");
        List<String> filesBefore = productFiles();

        mvc.perform(multipart(HttpMethod.PUT, "/api/products/{pno}", before.getPno())
                        .file(productPart("""
                                {"pname":"%s","onSale":true,"stock":1,"version":%d,"keepImages":[]}
                                """.formatted("가".repeat(201), before.getVersion())))
                        .file(image("C.png")))
                .andExpect(status().isBadRequest());

        assertThat(productFiles()).isEqualTo(filesBefore);   // 새 이미지 C 삭제됨
        assertThat(exists(before.getUpload1())).isTrue();     // 기존 A 유지 (keepImages=[]였지만 롤백)
        assertThat(reload(before).getImages()).isEqualTo(before.getImages());
    }

    private MockMultipartHttpServletRequestBuilder modify(ProductEntity p, String keepImagesJson) {
        String keep = keepImagesJson == null ? "" : ",\"keepImages\":" + keepImagesJson;
        return (MockMultipartHttpServletRequestBuilder) multipart(HttpMethod.PUT, "/api/products/{pno}", p.getPno())
                .file(productPart("""
                        {"pname":"수정된상품","onSale":true,"stock":4,"version":%d%s}
                        """.formatted(p.getVersion(), keep)));
    }

    private ProductEntity registerWithImages(String... names) {
        List<MultipartFile> images = new ArrayList<>();
        for (String name : names) {
            images.add(TestImages.pngPart(name));
        }
        ProductResponse p = productService.register(new ProductCreateRequest("수정테스트상품", "수정 테스트", 1000, true, 1), images);
        createdProducts.add(p.pno());
        ProductEntity entity = productRepository.findById(p.pno()).orElseThrow();
        entity.getImages().forEach(img -> touchedFiles.add(img.fileId()));
        return entity;
    }

    private ProductEntity reload(ProductEntity p) {
        ProductEntity e = productRepository.findById(p.getPno()).orElseThrow();
        e.getImages().forEach(img -> touchedFiles.add(img.fileId()));
        return e;
    }

    private static MockMultipartFile image(String name) {
        return TestImages.pngPart(name);
    }

    // CDN 캐시를 거치지 않는 인증 API로 존재 확인
    private boolean exists(String fileId) {
        return existsKey("products/" + fileId);
    }

    private boolean thumbExists(String fileId) {
        return existsKey("products/thumbs/" + ThumbnailGenerator.thumbnailId(fileId));
    }

    private boolean existsKey(String key) {
        return supabaseStorageRestClient.get()
                .uri("/object/authenticated/" + properties.bucket() + "/" + key)
                .exchange((req, res) -> res.getStatusCode().is2xxSuccessful());
    }

    private List<String> productFiles() {
        List<java.util.Map<String, Object>> list = supabaseStorageRestClient.post()
                .uri("/object/list/" + properties.bucket())
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("prefix", "products", "limit", 1000))
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<java.util.Map<String, Object>>>() {});
        List<java.util.Map<String, Object>> thumbs = supabaseStorageRestClient.post()
                .uri("/object/list/" + properties.bucket())
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("prefix", "products/thumbs", "limit", 1000))
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {});
        return Stream.concat(list.stream(), thumbs.stream().map(m -> java.util.Map.of("name", "thumbs/" + m.get("name"))))
                .map(m -> (String) m.get("name")).sorted().toList();
    }
}
