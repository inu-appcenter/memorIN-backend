package com.memorin.domain.post_comments.dto.response;

import com.memorin.domain.post_comments.entity.PostComments;

import java.time.LocalDateTime;

public record PostCommentResponse(
        String commentId,
        String authorId,
        String body,       // 삭제됐으면 placeholder로 대체
        boolean deleted,
        String parentId,
        LocalDateTime createdAt
) {
    public static PostCommentResponse from(PostComments c) {
        boolean deleted = c.isDeleted();
        return new PostCommentResponse(
                c.getId().toString(),
                deleted ? null : c.getUser().getId().toString(), // 작성자 정보도 감출지는 정책 선택
                deleted ? "삭제된 댓글입니다." : c.getBody(),
                deleted,
                c.getParent() != null ? c.getParent().getId().toString() : null,
                c.getCreatedAt()
        );
    }
}