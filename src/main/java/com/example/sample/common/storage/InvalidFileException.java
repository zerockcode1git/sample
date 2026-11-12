package com.example.sample.common.storage;

import com.example.sample.common.exception.BusinessException;
import com.example.sample.common.exception.ErrorCode;

/** 빈 파일, 크기 초과, 이미지가 아닌 파일, 첨부 개수 초과 */
public class InvalidFileException extends BusinessException {

    public InvalidFileException(String message) {
        super(ErrorCode.INVALID_FILE, message);
    }
}
