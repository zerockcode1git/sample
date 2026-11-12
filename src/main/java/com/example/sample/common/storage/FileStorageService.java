package com.example.sample.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소. 구현체를 바꾸면 Supabase 외의 저장소(S3, 로컬 등)로 교체할 수 있습니다.
 * 파일은 {@code {directory}/{fileId}} 키로 저장되고, DB에는 {@link #upload}가 돌려준 fileId({uuid}.{확장자})만 저장합니다.
 */
public interface FileStorageService {

    /**
     * 파일을 {@code {directory}/{uuid}.{확장자}}로 업로드하고 파일 ID를 돌려줍니다.
     * 확장자는 원본 파일의 확장자(소문자)입니다.
     *
     * @param directory 저장할 폴더 (예: "products")
     * @return 파일 ID (예: "3f83f0f8-4cf3-4840-8133-58ee2631fcce.png")
     */
    String upload(MultipartFile file, String directory);

    /** 파일을 삭제합니다. 없는 파일이어도 예외를 던지지 않습니다. */
    void delete(String directory, String fileId);

    /** 조회 URL. fileId가 비어 있으면 null, 이미 http(s) URL이면 그대로 돌려줍니다. */
    String getUrl(String directory, String fileId);
}
