package com.memorin.global.exception;

import com.memorin.global.common.ErrorCode;

// 게시물 도메인 예외.
// BusinessException을 상속해야 GlobalExceptionHandler가 ErrorCode에 맞는 상태코드로 변환한다.
// RuntimeException을 직접 상속하면 전부 500(COMMON_001)으로 떨어진다.
public class PostExceptions {

    private PostExceptions() {}

    public static class PostNotFoundException extends BusinessException {
        public PostNotFoundException(String postId) {
            super(ErrorCode.POST_001, "게시물을 찾을 수 없습니다: " + postId);
        }
    }

    public static class PostAccessDeniedException extends BusinessException {
        public PostAccessDeniedException() {
            super(ErrorCode.POST_002);
        }
    }

    public static class InvalidCursorException extends BusinessException {
        public InvalidCursorException(Throwable cause) {
            super(ErrorCode.POST_003, ErrorCode.POST_003.getMessage(), cause);
        }
    }

}
