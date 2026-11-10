package com.example.sample.product.exception;

import com.example.sample.common.exception.BusinessException;
import com.example.sample.common.exception.ErrorCode;

/** 증감 결과 재고가 음수가 되는 경우 (예: 재고 3개에서 5개 차감) */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(Long pno, int currentStock, int amount) {
        super(ErrorCode.INSUFFICIENT_STOCK,
                "재고가 부족합니다. pno=%d, 현재 재고=%d, 요청 증감=%d".formatted(pno, currentStock, amount));
    }
}
