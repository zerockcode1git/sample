package com.example.sample.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Supabase Storage(Public 버킷) 구현. 키는 {@code {directory}/{uuid}.{확장자}}.
 * 원본 이름은 저장소가 아니라 DB(img1~3)에 보관합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseFileStorageService implements FileStorageService {

    private final RestClient supabaseStorageRestClient;
    private final SupabaseStorageProperties properties;
    private final ImageFileValidator validator;

    @Override
    public String upload(MultipartFile file, String directory) {
        validator.validate(file);
        // 확장자는 검증기가 허용 목록(jpg/jpeg/png/gif/webp)으로 확인했으므로 항상 있음
        String fileId = UUID.randomUUID() + "." + ImageFileValidator.extension(file.getOriginalFilename());
        String key = key(directory, fileId);
        try {
            supabaseStorageRestClient.post()
                    .uri(objectUri(key))
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException | RestClientException e) {
            throw new StorageException("파일 업로드에 실패했습니다. 파일=" + file.getOriginalFilename(), e);
        }
        log.info("[storage] 업로드 {} ({}, {} bytes, 원본={})", key, file.getContentType(), file.getSize(), file.getOriginalFilename());
        return fileId;
    }

    @Override
    public void upload(byte[] data, String contentType, String directory, String fileId) {
        String key = key(directory, fileId);
        try {
            supabaseStorageRestClient.post()
                    .uri(objectUri(key))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new StorageException("파일 업로드에 실패했습니다. 파일=" + key, e);
        }
        log.info("[storage] 업로드 {} ({}, {} bytes)", key, contentType, data.length);
    }

    @Override
    public void delete(String directory, String fileId) {
        String key = key(directory, fileId);
        try {
            supabaseStorageRestClient.delete()
                    .uri(objectUri(key))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[storage] 삭제 {}", key);
        } catch (RestClientException e) {
            // 정리 작업 실패가 본 요청을 깨뜨리지 않도록 로그만 남김
            log.warn("[storage] 삭제 실패 {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String getUrl(String directory, String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        if (fileId.startsWith("http://") || fileId.startsWith("https://")) {
            return fileId;
        }
        return properties.publicUrl(key(directory, fileId));
    }

    private static String key(String directory, String fileId) {
        return directory + "/" + fileId;
    }

    // 키를 {path} 템플릿 변수로 넘기면 '/'가 %2F로 인코딩되므로 문자열로 이어 붙입니다.
    private String objectUri(String key) {
        return "/object/" + properties.bucket() + "/" + key;
    }
}
