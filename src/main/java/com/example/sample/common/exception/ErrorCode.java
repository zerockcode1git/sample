package com.example.sample.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    PRODUCT_CONFLICT(HttpStatus.CONFLICT, "다른 사용자가 상품을 먼저 변경했습니다. 다시 조회한 뒤 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
