package com.memorin.global.exception;

import com.memorin.global.common.ErrorCode;

// 비즈니스 규칙 위반 시 어디서든 던지는 예외.
// GlobalExceptionHandler가 받아 ErrorCode에 맞는 응답으로 변환한다.
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
