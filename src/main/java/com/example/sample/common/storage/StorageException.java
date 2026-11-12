package com.example.sample.common.storage;

import com.example.sample.common.exception.BusinessException;
import com.example.sample.common.exception.ErrorCode;

/** 저장소(Supabase Storage) 호출 실패. 네트워크 오류, 인증 실패, 저장소 측 거부 등 */
public class StorageException extends BusinessException {

    public StorageException(String message, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, message, cause);
    }
}
