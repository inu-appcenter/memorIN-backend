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
    AUTH_002(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // 회원 (member)
    USER_001(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
    USER_002(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    USER_003(HttpStatus.CONFLICT, "이미 사용 중인 이름입니다."),

    // 미디어 (media)
    MEDIA_001(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다"),
    MEDIA_002(HttpStatus.FORBIDDEN, "스토리지 용량을 초과했습니다"),
    MEDIA_003(HttpStatus.INTERNAL_SERVER_ERROR, "미디어 저장소 처리 중 오류가 발생했습니다"),
    MEDIA_004(HttpStatus.NOT_FOUND, "존재하지 않는 미디어입니다");

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
