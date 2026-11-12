package com.example.sample.product.controller;

import com.example.sample.common.exception.BusinessException;
import com.example.sample.common.exception.ErrorCode;
import com.example.sample.common.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * {@link ProductController}에서 발생한 예외를 {status, code, message} 형식의 응답으로 바꿉니다.
 * 전역 처리기보다 먼저 적용되도록 우선순위를 가장 높게 둡니다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductControllerAdvice {

    // 상품 없음(404), 재고/입력 오류(400), 충돌·재고 부족(409)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[{}] {}", code.name(), e.getMessage());
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }

    // 예: /api/products/abc, page=abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "'%s' 값이 올바르지 않습니다. 요청값=%s".formatted(e.getName(), e.getValue());
        return badRequest(message);
    }

    // 본문 없음, JSON 형식 오류, 숫자 필드에 문자열 등
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[INVALID_INPUT] 요청 본문을 읽을 수 없음: {}", e.getMessage());
        return badRequest("요청 본문(JSON) 형식이 올바르지 않습니다.");
    }

    // 등록 요청에 product 파트가 없음
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return badRequest("'%s' 파트가 필요합니다.".formatted(e.getRequestPartName()));
    }

    // product 파트의 Content-Type이 application/json이 아님
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return badRequest("지원하지 않는 형식입니다(%s). 등록은 multipart/form-data, product 파트는 application/json으로 보내 주세요."
                .formatted(e.getContentType()));
    }

    // DB 제약 위반 (예: 상품명 200자 초과)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("[INVALID_INPUT] DB 제약 위반: {}", e.getMostSpecificCause().getMessage());
        return badRequest("입력값이 저장 조건(길이, 필수값 등)을 만족하지 않습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("상품 API 처리 중 예상하지 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "INTERNAL_ERROR", "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
    }
}
