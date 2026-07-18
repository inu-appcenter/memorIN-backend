package com.memorin.global.exception;

public class PostExceptions {

    private PostExceptions() {}

    public static class PostNotFoundException extends RuntimeException {
        public PostNotFoundException(String postId) {
            super("게시물을 찾을 수 없습니다: " + postId); // POST_004
        }
    }

    public static class PostAccessDeniedException extends RuntimeException {
        public PostAccessDeniedException() {
            super("게시물에 접근할 권한이 없습니다."); // POST_003
        }
    }

}
