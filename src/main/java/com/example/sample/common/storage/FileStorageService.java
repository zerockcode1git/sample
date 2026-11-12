package com.example.sample.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소. 구현체를 바꾸면 Supabase 외의 저장소(S3, 로컬 등)로 교체할 수 있습니다.
 * DB에는 {@link #upload}가 돌려준 경로만 저장하고, 화면용 URL은 {@link #getUrl}로 만듭니다.
 */
public interface FileStorageService {

    /**
     * 파일을 업로드하고 버킷 안의 저장 경로를 돌려줍니다.
     *
     * @param directory 저장할 상위 폴더 (예: "products")
     * @return 저장 경로 (예: "products/2026/11/12/{uuid}_front.png")
     */
    String upload(MultipartFile file, String directory);

    /** 저장 경로의 파일을 삭제합니다. 없는 파일이어도 예외를 던지지 않습니다. */
    void delete(String path);

    /** 저장 경로를 조회 URL로 바꿉니다. null이면 null, 이미 http(s) URL이면 그대로 돌려줍니다. */
    String getUrl(String path);
}
