package com.example.sample.common.storage;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 업로드 이미지 검증. 저장소에 올리기 전에 모든 파일을 먼저 검사해서, 일부만 올라가는 상황을 줄입니다.
 */
@Component
public class ImageFileValidator {

    /** 3MB 미만만 허용 (버킷 file_size_limit = 3MB) */
    public static final long MAX_FILE_SIZE = 3L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    public void validate(List<MultipartFile> files, int maxCount) {
        if (files.size() > maxCount) {
            throw new InvalidFileException("이미지는 최대 %d개까지 업로드할 수 있습니다. 요청=%d개".formatted(maxCount, files.size()));
        }
        files.forEach(this::validate);
    }

    public void validate(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new InvalidFileException("빈 파일은 업로드할 수 없습니다. 파일=" + name);
        }
        if (file.getSize() >= MAX_FILE_SIZE) {
            throw new InvalidFileException("이미지는 3MB 미만이어야 합니다. 파일=%s, 크기=%d bytes".formatted(name, file.getSize()));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidFileException("이미지 파일만 업로드할 수 있습니다. 파일=%s, 형식=%s".formatted(name, contentType));
        }
        String ext = extension(name);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new InvalidFileException("허용되지 않는 확장자입니다. 허용=%s, 파일=%s".formatted(ALLOWED_EXTENSIONS, name));
        }
    }

    /** 소문자 확장자. 없으면 빈 문자열 */
    public static String extension(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }
}
