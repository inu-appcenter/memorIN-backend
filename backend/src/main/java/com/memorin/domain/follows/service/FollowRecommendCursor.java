package com.memorin.domain.follows.service;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class FollowRecommendCursor {

    private FollowRecommendCursor() {}

    public record Cursor(long score, UUID candidateId) {}

    public static String encode(long score, UUID candidateId) {
        String raw = score + ":" + candidateId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            return new Cursor(Long.parseLong(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception e) {
            // TODO: ErrorCode.FOLLOW_005는 임시 이름이야. 실제 ErrorCode enum에
            // 커서 파싱 실패용 코드가 있는지, 없다면 어떤 이름으로 추가할지 확인해줘.
            throw new BusinessException(ErrorCode.FOLLOW_005, "잘못된 커서입니다.");
        }
    }
}
