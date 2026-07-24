package com.memorin.domain.posts.service;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * asOf: 이 추천 피드 "세션"의 기준 시각. 페이지를 넘겨도 이 값은 고정해서 재사용해야
 * score 계산 결과가 페이지마다 흔들리지 않는다 (현재 시각을 다시 넣으면 안 됨).
 */
public class RecommendFeedCursor {

    private RecommendFeedCursor() {}

    public record Cursor(Instant asOf, double score, UUID postId) {}

    public static String encode(Instant asOf, double score, UUID postId) {
        String raw = asOf.toEpochMilli() + ":" + score + ":" + postId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Double.parseDouble(parts[1]), UUID.fromString(parts[2]));
        } catch (Exception e) {
            // 커서는 서버가 발급한 불투명 문자열이므로, 파싱 실패는 항상 클라이언트 잘못이다.
            // 원인 예외 메시지는 응답에 싣지 않고 cause로만 넘겨 로그에 남긴다.
            throw new BusinessException(ErrorCode.POST_003, ErrorCode.POST_003.getMessage(), e);
        }
    }
}