package com.example.sample.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Supabase Storage REST API 호출용 {@link RestClient}를 등록합니다.
 * 기본 URL과 인증 헤더(apikey, Authorization: Bearer)가 미리 설정되어 있습니다.
 */
@Configuration
@EnableConfigurationProperties(SupabaseStorageProperties.class)
public class SupabaseStorageConfig {

    @Bean
    public RestClient supabaseStorageRestClient(SupabaseStorageProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader("apikey", properties.serviceKey())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                .build();
    }
}
