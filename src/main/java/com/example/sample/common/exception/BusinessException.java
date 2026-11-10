package com.example.sample.common.exception;

import lombok.Getter;

/**
 * 비즈니스 계층에서 발생하는 모든 예외의 상위 클래스.
 * 전역 예외 처리기가 {@link ErrorCode}를 보고 HTTP 응답으로 변환합니다.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
