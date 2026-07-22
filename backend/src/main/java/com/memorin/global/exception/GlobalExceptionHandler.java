package com.memorin.global.exception;

import com.memorin.global.common.ApiResponse;
import com.memorin.global.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 컨트롤러의 예외를 한곳에서 받아 공통 응답 포맷으로 변환한다.
// 컨트롤러/서비스에는 try-catch를 두지 않는다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode, e.getMessage()));
    }

    // @Valid 검증 실패 (DTO 필드 검증)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.COMMON_002.getMessage());
        return ResponseEntity.status(ErrorCode.COMMON_002.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_002, message));
    }

    // 잘못된 파라미터(타입 변환 실패, 검증되지 않은 인자 등) → 400.
    // 이 핸들러가 없으면 아래 Exception 핸들러로 떨어져 클라이언트 잘못이 500으로 보고된다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(ErrorCode.COMMON_002.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_002, e.getMessage()));
    }

    // 위에서 잡지 못한 모든 예외 → 500. 원인 파악을 위해 스택트레이스를 남긴다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
        log.error("예상치 못한 예외 발생", e);
        return ResponseEntity.status(ErrorCode.COMMON_001.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_001));
    }
}
