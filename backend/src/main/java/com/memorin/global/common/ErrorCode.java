package com.memorin.global.common;

import org.springframework.http.HttpStatus;

// 서비스 전체 에러 사전. 에러 코드는 "도메인_번호" 형식으로 여기에만 추가한다.
// enum 이름 자체가 클라이언트에 내려가는 code 문자열이 된다.
public enum ErrorCode {

    // 공통
    COMMON_001(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
    COMMON_002(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    COMMON_003(HttpStatus.FORBIDDEN, "해당 요청에 대한 권한이 없습니다"),
    COMMON_004(HttpStatus.NOT_FOUND, "요청하신 경로를 찾을 수 없습니다"),
    COMMON_005(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않는 요청 메서드입니다"),

    // 인증 (auth)
    AUTH_001(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    AUTH_002(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_003(HttpStatus.UNAUTHORIZED, "다시 로그인하여 주세요."),
    AUTH_004(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),

    // 회원 (member)
    USER_001(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
    USER_002(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    USER_003(HttpStatus.CONFLICT, "이미 사용 중인 이름입니다."),

    // 팔로우 (follows)
    FOLLOW_001(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    FOLLOW_002(HttpStatus.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다."),
    FOLLOW_003(HttpStatus.CONFLICT, "이미 존재하는 팔로우 관계입니다."),
    FOLLOW_004(HttpStatus.FORBIDDEN, "처리할 수 없는 팔로우 요청입니다."),

    // 게시물 (post)
    POST_001(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다"),
    POST_002(HttpStatus.FORBIDDEN, "게시물에 접근할 권한이 없습니다"),
    POST_003(HttpStatus.BAD_REQUEST, "잘못된 cursor 값입니다"),

    // 댓글 (post_comment)
    COMMENT_001(HttpStatus.NOT_FOUND, "댓글이 존재하지 않습니다."),
    COMMENT_002(HttpStatus.FORBIDDEN, "본인만 댓글을 삭제할 수 있습니다."),
    COMMENT_003(HttpStatus.NOT_FOUND, "부모 댓글이 존재하지 않습니다."),
    COMMENT_004(HttpStatus.BAD_REQUEST, "대댓글에는 답글을 달 수 없습니다."),
    COMMENT_005(HttpStatus.FORBIDDEN, "비공개 게시물에는 댓글을 달 수 없습니다."),
    COMMENT_006(HttpStatus.FORBIDDEN, "삭제된 댓글은 수정할 수 없습니다."),

    // 미디어 (media)
    MEDIA_001(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다"),
    MEDIA_002(HttpStatus.FORBIDDEN, "스토리지 용량을 초과했습니다"),
    MEDIA_003(HttpStatus.INTERNAL_SERVER_ERROR, "미디어 저장소 처리 중 오류가 발생했습니다"),
    MEDIA_004(HttpStatus.NOT_FOUND, "존재하지 않는 미디어입니다"),
    MEDIA_005(HttpStatus.BAD_REQUEST, "업로드 가능한 최대 파일 크기를 초과했습니다"),
    MEDIA_006(HttpStatus.FORBIDDEN, "미디어에 접근할 권한이 없습니다"),
    MEDIA_007(HttpStatus.NOT_FOUND, "업로드 예약을 찾을 수 없거나 만료되었습니다");

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
