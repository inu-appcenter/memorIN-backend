package com.memorin.domain.posts.service;

import com.memorin.global.exception.PostExceptions;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Base64;

// "{recordedDate}:{postId}" 를 base64로 인코딩/디코딩하는 커서 유틸리티.
/**
 * 커서 = 정렬 기준 컬럼(recordedDate) + tie-breaker(postId)를 묶어서 불투명하게 인코딩한 값.
 * 정렬 기준 하나만으로는 같은 recordedDate를 가진 항목들의 순서가 불안정해지기 때문에
 * 반드시 postId(PK)를 함께 넣는다.
 */
public class PostCursor {

    private PostCursor() {}

    public record Cursor(Date recordedDate, String postId) {}

    public static String encode(Date recordedDate, String postId) {
        String raw = recordedDate.toLocalDate() + ":" + postId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            return new Cursor(Date.valueOf(LocalDate.parse(parts[0])), parts[1]);
        } catch (Exception e) {
            throw new PostExceptions.InvalidCursorException(e);
        }
    }
}
