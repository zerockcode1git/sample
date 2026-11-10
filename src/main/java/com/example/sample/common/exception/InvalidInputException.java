package com.example.sample.common.exception;

public class InvalidInputException extends BusinessException {

    public InvalidInputException(String message) {
        super(ErrorCode.INVALID_INPUT, message);
    }
}
