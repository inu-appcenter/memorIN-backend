package com.memorin.global.common;

import org.springframework.http.HttpStatus;

// 서비스 전체 에러 사전. 에러 코드는 "도메인_번호" 형식으로 여기에만 추가한다.
// enum 이름 자체가 클라이언트에 내려가는 code 문자열이 된다.
public enum ErrorCode {

    // 공통
    COMMON_001(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
    COMMON_002(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),

    // 인증 (auth)
    AUTH_001(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),

    // 회원 (member)
    MEMBER_001(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),

    // 미디어 (media)
    MEDIA_001(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
