package com.example.sample.product.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 DB 사용, 테스트마다 롤백. 이미지 없이 등록하므로 Storage는 사용하지 않음 (이미지 등록은 ProductRegisterUploadTest)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired MockMvc mvc;

    private long register(String pname) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/products").file(productPart("""
                                {"pname":"%s","pdesc":"컨트롤러 테스트","price":1000,"onSale":true,"stock":5}
                                """.formatted(pname))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pname").value(pname))
                .andReturn();
        Matcher m = Pattern.compile("\"pno\":(\\d+)").matcher(result.getResponse().getContentAsString());
        m.find();
        return Long.parseLong(m.group(1));
    }

    static MockMultipartFile productPart(String json) {
        return new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void crud() throws Exception {
        long pno = register("컨트롤러상품");

        mvc.perform(get("/api/products/{pno}", pno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(5));

        mvc.perform(multipart(HttpMethod.PUT, "/api/products/{pno}", pno).file(productPart("""
                                {"pname":"수정됨","onSale":false,"stock":3,"version":0}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pname").value("수정됨"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(delete("/api/products/{pno}", pno)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/{pno}", pno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void listIsOneBased() throws Exception {
        register("페이징테스트A");
        register("페이징테스트B");

        mvc.perform(get("/api/products").param("page", "1").param("size", "1").param("keyword", "페이징테스트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pname").value("페이징테스트B")) // pno desc
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mvc.perform(get("/api/products").param("page", "2").param("size", "1").param("keyword", "페이징테스트"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.content[0].pname").value("페이징테스트A"));
    }

    @Test
    void searchByDescription() throws Exception {
        register("설명검색상품");
        mvc.perform(get("/api/products").param("keyword", "컨트롤러 테스트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pname").value("설명검색상품"));
    }

    @Test
    void errors() throws Exception {
        mvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'pno' 값이 올바르지 않습니다. 요청값=abc"));
        mvc.perform(multipart("/api/products").file(productPart("{bad json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 본문(JSON) 형식이 올바르지 않습니다."));
        mvc.perform(multipart("/api/products").file(productPart("""
                                {"pname":"음수재고","price":1000,"onSale":true,"stock":-1}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STOCK"));

        // product 파트 없음
        mvc.perform(multipart("/api/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'product' 파트가 필요합니다."));

        long pno = register("버전충돌");
        mvc.perform(multipart(HttpMethod.PUT, "/api/products/{pno}", pno).file(productPart("""
                                {"pname":"x","onSale":true,"stock":1,"version":99}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CONFLICT"));
    }
}
