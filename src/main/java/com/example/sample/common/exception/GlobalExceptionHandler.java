package com.example.sample.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[{}] {}", code.name(), e.getMessage());
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }

    // spring.servlet.multipart 크기 제한 초과. 컨트롤러가 정해지기 전에 발생하므로 전역 처리기에서 받습니다.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("[INVALID_FILE] 업로드 크기 초과: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_FILE.getStatus()).body(ErrorResponse.of(ErrorCode.INVALID_FILE,
                "업로드 크기를 초과했습니다. 이미지 1개 3MB 미만, 요청 전체 10MB 이하로 보내 주세요."));
    }
}
