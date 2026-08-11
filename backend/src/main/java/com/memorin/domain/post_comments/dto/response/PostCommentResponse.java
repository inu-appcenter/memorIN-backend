package com.memorin.domain.post_comments.dto.response;

import com.memorin.domain.emoji.dto.response.EmojiSummary;
import com.memorin.domain.post_comments.entity.PostComments;
import com.memorin.domain.users.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public record PostCommentResponse(
        String commentId,
        String authorId,
        String authorUsername,      // 삭제된 댓글이면 null
        String authorDisplayName,   // 닉네임. FE 댓글 패널/바텀시트가 그리는 값
        String authorProfileImageKey, // MinIO 키. 다운로드 URL은 FE가 별도 발급
        String body,       // 삭제됐으면 placeholder로 대체
        boolean deleted,
        String parentId,
        LocalDateTime createdAt,
        List<EmojiSummary> emojis   // 이 댓글에 달린 이모지 집계. 없으면 빈 리스트
) {
    // 작성/수정 응답처럼 이모지가 아직 없는 경로용.
    public static PostCommentResponse from(PostComments c) {
        return of(c, List.of());
    }

    // 스레드 조회용. emojis는 배치 집계 결과에서 꺼내 넣는다.
    //
    // 주의: c.getUser()에 접근하므로 조회 쿼리가 반드시 user를 함께 가져와야 한다
    // (PostCommentRepository.findThreadByPostId의 JOIN FETCH). 안 그러면 댓글 1건당 SELECT 1번씩
    // 늘어나는 N+1이 된다. CommentThreadQueryCountTest가 이걸 고정한다.
    public static PostCommentResponse of(PostComments c, List<EmojiSummary> emojis) {
        boolean deleted = c.isDeleted();

        // tombstone은 작성자 정보를 전부 감춘다. 본문만 지우고 닉네임이 남으면
        // "누가 지웠는지"가 그대로 노출된다.
        User author = deleted ? null : c.getUser();

        return new PostCommentResponse(
                c.getId().toString(),
                author != null ? author.getId().toString() : null,
                author != null ? author.getUsername() : null,
                author != null ? author.getDisplayName() : null,
                author != null ? author.getProfileImageKey() : null,
                deleted ? "삭제된 댓글입니다." : c.getBody(),
                deleted,
                c.getParent() != null ? c.getParent().getId().toString() : null,
                c.getCreatedAt(),
                emojis != null ? emojis : List.of()
        );
    }
}
