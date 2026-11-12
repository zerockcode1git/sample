package com.example.sample.product.controller;

import com.example.sample.common.storage.FileStorageService;
import com.example.sample.common.storage.SupabaseStorageProperties;
import com.example.sample.product.entity.ProductEntity;
import com.example.sample.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.example.sample.product.controller.ProductControllerTest.productPart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이미지 포함 상품 등록. 실제 DB + 실제 Supabase Storage 사용.
 * 롤백 시 파일 삭제를 검증하려면 트랜잭션이 실제로 끝나야 하므로 @Transactional을 쓰지 않고 직접 정리합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductRegisterUploadTest {

    private static final String PRODUCT_JSON = """
            {"pname":"%s","pdesc":"이미지 등록 테스트","price":15000,"onSale":true,"stock":3}
            """;

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepository;
    @Autowired FileStorageService fileStorageService;
    @Autowired SupabaseStorageProperties properties;
    @Autowired RestClient supabaseStorageRestClient;

    private final List<Long> createdProducts = new ArrayList<>();

    @BeforeEach
    void requireKey() {
        assumeTrue(StringUtils.hasText(properties.serviceKey()), "supabase.storage.service-key 없음");
    }

    @AfterEach
    void cleanup() {
        for (Long pno : createdProducts) {
            productRepository.findById(pno).ifPresent(p -> {
                Stream.of(p.getImg1(), p.getImg2(), p.getImg3()).filter(StringUtils::hasText).forEach(fileStorageService::delete);
                productRepository.delete(p);
            });
        }
    }

    @Test
    void registerWithImages() throws Exception {
        MvcResult result = mvc.perform(multipart("/api/products")
                        .file(productPart(PRODUCT_JSON.formatted("이미지상품")))
                        .file(image("front.png", 300))
                        .file(image("back.PNG", 200)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.img1").value(org.hamcrest.Matchers.startsWith(properties.publicUrl("products/"))))
                .andExpect(jsonPath("$.img2").isString())
                .andExpect(jsonPath("$.img3").isEmpty())
                .andReturn();
        long pno = pno(result);
        createdProducts.add(pno);

        // DB에는 URL이 아니라 저장 경로만 저장
        ProductEntity saved = productRepository.findById(pno).orElseThrow();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertThat(saved.getImg1()).matches("products/" + today + "/[0-9a-f-]{36}_front\\.png");
        assertThat(saved.getImg2()).endsWith("_back.png"); // 확장자는 소문자로
        assertThat(saved.getImg3()).isNull();

        // Public URL로 실제 조회 가능
        byte[] downloaded = RestClient.create().get().uri(URI.create(fileStorageService.getUrl(saved.getImg1())))
                .retrieve().body(byte[].class);
        assertThat(downloaded).isNotEmpty();
        System.out.println("[register] pno=" + pno + ", img1=" + fileStorageService.getUrl(saved.getImg1()));
    }

    @Test
    void registerWithoutImages() throws Exception {
        MvcResult result = mvc.perform(multipart("/api/products").file(productPart(PRODUCT_JSON.formatted("이미지없음"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.img1").isEmpty())
                .andReturn();
        createdProducts.add(pno(result));
    }

    @Test
    void rejectInvalidFiles() throws Exception {
        long before = productRepository.count();

        // 이미지가 아닌 파일
        mvc.perform(multipart("/api/products").file(productPart(PRODUCT_JSON.formatted("텍스트첨부")))
                        .file(new MockMultipartFile("images", "memo.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        // 3MB 이상
        mvc.perform(multipart("/api/products").file(productPart(PRODUCT_JSON.formatted("큰파일")))
                        .file(new MockMultipartFile("images", "big.png", "image/png", new byte[3 * 1024 * 1024])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        // 4개 이상
        mvc.perform(multipart("/api/products").file(productPart(PRODUCT_JSON.formatted("개수초과")))
                        .file(image("1.png", 10)).file(image("2.png", 10)).file(image("3.png", 10)).file(image("4.png", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        assertThat(productRepository.count()).isEqualTo(before);
    }

    // 이미지 업로드 후 DB 저장이 실패하면(상품명 200자 초과) 올린 이미지를 삭제
    @Test
    void deleteUploadedImagesWhenSaveFails() throws Exception {
        List<String> beforeFiles = todayFiles();

        mvc.perform(multipart("/api/products")
                        .file(productPart(PRODUCT_JSON.formatted("가".repeat(201))))
                        .file(image("rollback.png", 100)))
                .andExpect(status().isBadRequest());

        assertThat(todayFiles()).isEqualTo(beforeFiles);
    }

    private List<String> todayFiles() {
        String prefix = "products/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        List<Map<String, Object>> list = supabaseStorageRestClient.post()
                .uri("/object/list/" + properties.bucket())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prefix", prefix, "limit", 1000))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return list.stream().map(m -> (String) m.get("name")).sorted().toList();
    }

    private static long pno(MvcResult result) throws Exception {
        Matcher m = Pattern.compile("\"pno\":(\\d+)").matcher(result.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static MockMultipartFile image(String filename, int sizePx) throws IOException {
        BufferedImage img = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(sizePx);
        for (int y = 0; y < sizePx; y++) {
            for (int x = 0; x < sizePx; x++) {
                img.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("images", filename, MediaType.IMAGE_PNG_VALUE, out.toByteArray());
    }
}
