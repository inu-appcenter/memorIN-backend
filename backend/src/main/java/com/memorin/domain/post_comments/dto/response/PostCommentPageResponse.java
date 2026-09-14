package com.memorin.domain.post_comments.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * 댓글 스레드 한 페이지.
 *
 * <p><b>페이징 단위는 "최상위 댓글"이다.</b> 평면 목록을 그대로 자르면 페이지 경계에서 부모와
 * 대댓글이 갈라져 FE가 트리를 그릴 수 없다. 그래서 최상위 댓글만 커서로 넘기고,
 * 그 페이지에 속한 최상위 댓글의 대댓글은 <b>개수와 무관하게 전부</b> 함께 실어 보낸다.
 *
 * <p>따라서 {@code items.size()}는 {@code size} 파라미터보다 클 수 있다.
 * {@code size}가 세는 것은 최상위 댓글 수다.
 *
 * <p>{@code nextCursor}는 이 페이지 마지막 <b>최상위 댓글</b>의 id다. 대댓글의 id가 아니다.
 */
public record PostCommentPageResponse(
    List<PostCommentResponse> items,
    UUID nextCursor,
    boolean hasNext
) {
}
