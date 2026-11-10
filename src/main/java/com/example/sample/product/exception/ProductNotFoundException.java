package com.example.sample.product.exception;

import com.example.sample.common.exception.BusinessException;
import com.example.sample.common.exception.ErrorCode;

public class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException(Long pno) {
        super(ErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다. pno=" + pno);
    }
}
