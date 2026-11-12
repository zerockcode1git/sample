package com.example.sample.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SupabaseStorageConfigTest {

    @Autowired SupabaseStorageProperties properties;
    @Autowired RestClient supabaseStorageRestClient;

    @Test
    void propertiesBound() {
        assertThat(properties.url()).startsWith("https://").endsWith(".supabase.co");
        assertThat(properties.bucket()).isEqualTo("images");
        assertThat(properties.signedUrlExpiresIn()).isPositive();
    }

    // 실제 연결 확인: 키가 있을 때만 실행. GET /storage/v1/bucket/{bucket}
    @Test
    @EnabledIfEnvironmentVariable(named = "SUPABASE_SERVICE_KEY", matches = ".+")
    void bucketExists() {
        Map<String, Object> bucket = supabaseStorageRestClient.get()
                .uri("/bucket/{bucket}", properties.bucket())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(bucket).containsEntry("name", properties.bucket());
    }
}
