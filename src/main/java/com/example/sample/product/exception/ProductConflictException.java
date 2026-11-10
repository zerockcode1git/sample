package com.example.sample.product.exception;

import com.example.sample.common.exception.BusinessException;
import com.example.sample.common.exception.ErrorCode;

/** 조회 이후 다른 요청이 상품을 먼저 변경했거나(버전 불일치), 잠금을 얻지 못한 경우 */
public class ProductConflictException extends BusinessException {

    public ProductConflictException(Long pno) {
        super(ErrorCode.PRODUCT_CONFLICT, ErrorCode.PRODUCT_CONFLICT.getMessage() + " pno=" + pno);
    }

    public ProductConflictException(Long pno, Throwable cause) {
        super(ErrorCode.PRODUCT_CONFLICT, ErrorCode.PRODUCT_CONFLICT.getMessage() + " pno=" + pno, cause);
    }
}
