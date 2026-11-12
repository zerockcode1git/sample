package com.example.sample.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supabase Storage 접속 설정 ({@code supabase.storage.*}).
 *
 * @param url                프로젝트 URL (예: https://{ref}.supabase.co)
 * @param bucket             버킷 이름 (Public)
 * @param serviceKey         service_role 키. 서버에서만 사용하고 클라이언트에 노출하지 않습니다.
 * @param signedUrlExpiresIn Private 버킷 객체 조회용 서명 URL 유효 시간(초)
 *                           (현재 버킷은 Public이라 사용하지 않음)
 */
@ConfigurationProperties(prefix = "supabase.storage")
public record SupabaseStorageProperties(String url, String bucket, String serviceKey, Integer signedUrlExpiresIn) {

    public SupabaseStorageProperties {
        if (signedUrlExpiresIn == null) {
            signedUrlExpiresIn = 3600;
        }
    }

    /** Storage REST API 기본 경로: {url}/storage/v1 */
    public String apiBaseUrl() {
        return url + "/storage/v1";
    }

    /** Public 버킷 객체 조회 URL: {url}/storage/v1/object/public/{bucket}/{path} */
    public String publicUrl(String path) {
        return apiBaseUrl() + "/object/public/" + bucket + "/" + path;
    }
}
