package com.example.sample.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Supabase Storage(Public 버킷) 구현.
 * 파일 이름은 {@code {uuid}_{원본이름}.{확장자}}. UUID로 중복을 막고, 원본 이름은 알아보기 위해 붙입니다.
 * Supabase 객체 키는 한글 등 비ASCII 문자를 거부(InvalidKey)하므로 원본 이름은 영문/숫자/./_/-만 남깁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int MAX_BASE_NAME_LENGTH = 100;

    private final RestClient supabaseStorageRestClient;
    private final SupabaseStorageProperties properties;
    private final ImageFileValidator validator;

    @Override
    public String upload(MultipartFile file, String directory) {
        validator.validate(file);
        String path = "%s/%s/%s".formatted(directory, LocalDate.now().format(DATE_DIR),
                storedFileName(UUID.randomUUID(), file.getOriginalFilename()));
        try {
            supabaseStorageRestClient.post()
                    .uri(objectUri(path))
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException | RestClientException e) {
            throw new StorageException("파일 업로드에 실패했습니다. 파일=" + file.getOriginalFilename(), e);
        }
        log.info("[storage] 업로드 {} ({} bytes)", path, file.getSize());
        return path;
    }

    @Override
    public void delete(String path) {
        try {
            supabaseStorageRestClient.delete()
                    .uri(objectUri(path))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[storage] 삭제 {}", path);
        } catch (RestClientException e) {
            // 정리 작업 실패가 본 요청을 깨뜨리지 않도록 로그만 남김
            log.warn("[storage] 삭제 실패 {}: {}", path, e.getMessage());
        }
    }

    @Override
    public String getUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return properties.publicUrl(path);
    }

    /**
     * 저장 파일 이름: {uuid}_{원본이름}.{소문자 확장자}
     * <p>예: "front.PNG" → "{uuid}_front.png", "앞면 사진(1).jpg" → "{uuid}_1.jpg", "사진.png" → "{uuid}_image.png"
     */
    static String storedFileName(UUID uuid, String originalFilename) {
        String ext = ImageFileValidator.extension(originalFilename);
        return uuid + "_" + safeBaseName(originalFilename) + (ext.isEmpty() ? "" : "." + ext);
    }

    // 경로와 확장자를 떼고, 허용 문자(영문/숫자/./_/-) 외는 '_'로 바꿉니다. 남는 게 없으면 "image"
    private static String safeBaseName(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        name = StringUtils.stripFilenameExtension(StringUtils.getFilename(name));
        String safe = name.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._]+|[._]+$", "");
        if (safe.length() > MAX_BASE_NAME_LENGTH) {
            safe = safe.substring(0, MAX_BASE_NAME_LENGTH);
        }
        return safe.isEmpty() ? "image" : safe;
    }

    // 경로를 {path} 템플릿 변수로 넘기면 '/'가 %2F로 인코딩되므로 문자열로 이어 붙입니다.
    private String objectUri(String path) {
        return "/object/" + properties.bucket() + "/" + path;
    }
}
