package com.example.sample.common.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 실제 Supabase Storage에 업로드 후 삭제. service-key가 있을 때만 실행
@SpringBootTest
class SupabaseStorageUploadTest {

    private static final int MB = 1024 * 1024;

    @Autowired SupabaseStorageProperties properties;
    @Autowired RestClient supabaseStorageRestClient;

    private final List<String> uploaded = new ArrayList<>();

    @BeforeEach
    void requireKey() {
        assumeTrue(StringUtils.hasText(properties.serviceKey()), "supabase.storage.service-key 없음");
    }

    @AfterEach
    void cleanup() {
        for (String path : uploaded) {
            supabaseStorageRestClient.delete()
                    .uri(objectPath("/object/", path))
                    .retrieve().toBodilessEntity();
        }
    }

    @Test
    void bucketInfo() {
        Map<String, Object> bucket = supabaseStorageRestClient.get()
                .uri("/bucket/{bucket}", properties.bucket())
                .retrieve().body(new ParameterizedTypeReference<>() {});
        System.out.println("[bucket] " + bucket);
        assertThat(bucket).containsEntry("file_size_limit", 3 * MB);
    }

    @Test
    void uploadUnder3mb() throws IOException {
        byte[] png = noisePng(900, 900); // 압축이 안 되는 노이즈 이미지 → 약 2.4MB
        assertThat(png.length).isLessThan(3 * MB);

        String path = "test/" + UUID.randomUUID() + ".png";
        ResponseEntity<String> res = upload(path, png);
        uploaded.add(path);
        System.out.printf("[upload] %d bytes (%.2fMB) -> %s %s%n", png.length, png.length / (double) MB,
                res.getStatusCode(), res.getBody());
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        // Private 버킷: 서명 URL 발급 후 다운로드해서 크기 비교
        Map<String, Object> signed = supabaseStorageRestClient.post()
                .uri(objectPath("/object/sign/", path))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", 60))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        String signedUrl = properties.apiBaseUrl() + signed.get("signedURL");
        byte[] downloaded = RestClient.create().get().uri(java.net.URI.create(signedUrl)).retrieve().body(byte[].class);
        System.out.println("[download] " + downloaded.length + " bytes via signed URL");
        assertThat(downloaded).hasSize(png.length);
    }

    @Test
    void uploadOver3mb() throws IOException {
        byte[] png = noisePng(1100, 1000); // 약 3.1MB
        assertThat(png.length).isGreaterThan(3 * MB);

        String path = "test/" + UUID.randomUUID() + ".png";
        // 버킷 file_size_limit(3MB) 초과 → Storage가 413(EntityTooLarge)을 400 응답 본문에 담아 거부
        assertThatThrownBy(() -> {
            upload(path, png);
            uploaded.add(path);
        })
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(e -> {
                    String body = ((RestClientResponseException) e).getResponseBodyAsString();
                    System.out.printf("[upload >3MB] %d bytes -> %s%n", png.length, body);
                    assertThat(body).contains("EntityTooLarge");
                });
    }

    private ResponseEntity<String> upload(String path, byte[] data) {
        return supabaseStorageRestClient.post()
                .uri(objectPath("/object/", path))
                .contentType(MediaType.IMAGE_PNG)
                .body(data)
                .retrieve().toEntity(String.class);
    }

    // {path} 템플릿 변수로 넘기면 '/'가 %2F로 인코딩되어 서명 URL이 다른 경로를 가리키게 됩니다.
    private String objectPath(String prefix, String path) {
        return prefix + properties.bucket() + "/" + path;
    }

    private static byte[] noisePng(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
