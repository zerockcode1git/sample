package com.example.sample.product.controller;

import com.example.sample.common.storage.SupabaseStorageProperties;
import com.example.sample.product.entity.ProductEntity;
import com.example.sample.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 조회 (단건, 목록). 실제 DB 사용, 테스트마다 롤백.
 * Storage를 호출하지 않도록 상품은 Repository로 직접 저장합니다 (upload 값 → URL 변환만 검증).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductReadTest {

    private static final String UUID1 = "28d00fd0-c6ee-453f-8e6c-0584d806f8e7.png";

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepository;
    @Autowired SupabaseStorageProperties properties;

    private ProductEntity save(String pname, String img1, String upload1) {
        return productRepository.save(ProductEntity.builder()
                .pname(pname).pdesc("조회 테스트 설명").price(5000).onSale(true).stock(2)
                .img1(img1).upload1(upload1)
                .build());
    }

    @Test
    void getOne() throws Exception {
        ProductEntity p = save("조회테스트상품", "앞면 사진(1).png", UUID1);

        mvc.perform(get("/api/products/{pno}", p.getPno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pno").value(p.getPno()))
                .andExpect(jsonPath("$.pname").value("조회테스트상품"))
                .andExpect(jsonPath("$.img1").value("앞면 사진(1).png"))
                .andExpect(jsonPath("$.upload1").value(properties.publicUrl("products/" + UUID1)))
                .andExpect(jsonPath("$.img2").isEmpty())
                .andExpect(jsonPath("$.upload2").isEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void getOneErrors() throws Exception {
        mvc.perform(get("/api/products/{pno}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("상품을 찾을 수 없습니다. pno=999999999"));
        mvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void listWithImagesAndPaging() throws Exception {
        save("조회목록A", "a.png", null);
        save("조회목록B", "b.png", UUID1);
        save("조회목록C", null, null);

        // 1페이지: 최신순 C, B
        mvc.perform(get("/api/products").param("keyword", "조회목록").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].pname").value("조회목록C"))
                .andExpect(jsonPath("$.content[1].pname").value("조회목록B"))
                // 목록은 원본 대신 썸네일만: products/thumbs/{uuid}.jpg
                .andExpect(jsonPath("$.content[1].thumbnail").value(properties.publicUrl("products/thumbs/" + UUID1.replace(".png", ".jpg"))))
                .andExpect(jsonPath("$.content[1].imageCount").value(1))
                .andExpect(jsonPath("$.content[1].upload1").doesNotExist());

        // 2페이지: A, 마지막
        mvc.perform(get("/api/products").param("keyword", "조회목록").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pname").value("조회목록A"))
                .andExpect(jsonPath("$.content[0].thumbnail").isEmpty())
                .andExpect(jsonPath("$.content[0].imageCount").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));

        // 범위를 넘긴 페이지: 빈 목록
        mvc.perform(get("/api/products").param("keyword", "조회목록").param("page", "5").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void listDefaultsAndSearchByDescription() throws Exception {
        save("설명으로찾기", null, null);

        // 기본값 page=1, size=10
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));

        // 설명 검색, 대소문자/공백 무시는 Repository 테스트에서 확인
        mvc.perform(get("/api/products").param("keyword", "조회 테스트 설명"))
                .andExpect(jsonPath("$.content[0].pname").value("설명으로찾기"));

        // 검색 결과 없음
        mvc.perform(get("/api/products").param("keyword", "존재하지않는검색어zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // 범위를 벗어난 page/size는 오류 대신 조정
    @Test
    void listClampsPageAndSize() throws Exception {
        for (int i = 0; i < 22; i++) {
            save("조정테스트" + i, null, null);
        }
        mvc.perform(get("/api/products").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        mvc.perform(get("/api/products").param("page", "-5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        mvc.perform(get("/api/products").param("keyword", "조정테스트").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(20))   // 최대 20개
                .andExpect(jsonPath("$.totalElements").value(22));
        mvc.perform(get("/api/products").param("size", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(10));
        mvc.perform(get("/api/products").param("size", "-3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void listErrors() throws Exception {
        mvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'page' 값이 올바르지 않습니다. 요청값=abc"));
    }
}
