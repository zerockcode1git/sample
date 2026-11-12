package com.example.sample.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(description = "HTTP 상태 코드", example = "400") int status,
        @Schema(description = "에러 코드", example = "INVALID_FILE") String code,
        @Schema(description = "에러 메시지", example = "이미지는 3MB 미만이어야 합니다.") String message) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), message);
    }
}
