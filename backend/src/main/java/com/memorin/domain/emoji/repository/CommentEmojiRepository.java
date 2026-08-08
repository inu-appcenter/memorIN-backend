package com.memorin.domain.emoji.repository;

import com.memorin.domain.emoji.dto.response.EmojiCountDto;
import com.memorin.domain.emoji.entity.CommentEmoji;
import com.memorin.domain.emoji.entity.EmojiType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentEmojiRepository extends JpaRepository<CommentEmoji, UUID> {

    Optional<CommentEmoji> findByUserIdAndPostCommentsIdAndEmojiType(
        UUID userId, UUID commentId, EmojiType emojiType);

    long deleteByUserIdAndPostCommentsIdAndEmojiType(
        UUID userId, UUID commentId, EmojiType emojiType);

    // 댓글 목록용 집계 (N+1 방지)
    @Query("""
        SELECT new com.memorin.domain.emoji.dto.response.EmojiCountDto(
            ce.postComments.id, ce.emojiType, COUNT(ce),
            SUM(CASE WHEN ce.user.id = :meId THEN 1 ELSE 0 END)>0)
        FROM CommentEmoji ce
        WHERE ce.postComments.id IN :commentIds
        GROUP BY ce.postComments.id, ce.emojiType
        """)
    List<EmojiCountDto> countByCommentIds(List<UUID> commentIds, UUID meId);
}

