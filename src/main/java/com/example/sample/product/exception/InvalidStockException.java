package com.example.sample.product.exception;

import com.example.sample.common.exception.BusinessException;
import com.example.sample.common.exception.ErrorCode;

/** 재고 수량을 음수로 지정하려는 경우 (예: 수정 요청에 stock=-1) */
public class InvalidStockException extends BusinessException {

    public InvalidStockException(int stock) {
        super(ErrorCode.INVALID_STOCK, "재고 수량은 0 이상이어야 합니다. 요청값=" + stock);
    }
}
