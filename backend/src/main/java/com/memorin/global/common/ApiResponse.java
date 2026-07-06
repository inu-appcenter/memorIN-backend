package com.memorin.global.common;

// 모든 API 응답을 감싸는 공통 봉투.
// 성공: ApiResponse.ok(data) / 실패: ApiResponse.fail(errorCode)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), errorCode.getMessage()));
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), message));
    }

    public record ErrorBody(String code, String message) {
    }
}
