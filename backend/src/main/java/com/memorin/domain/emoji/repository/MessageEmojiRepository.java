package com.memorin.domain.emoji.repository;

import com.memorin.domain.emoji.dto.response.MessageEmojiCountDto;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.emoji.entity.MessageEmoji;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageEmojiRepository extends JpaRepository<MessageEmoji, UUID> {

    Optional<MessageEmoji> findByUserIdAndMessageIdAndEmojiType(
        UUID userId, UUID messageId, EmojiType emojiType);

    long deleteByUserIdAndMessageIdAndEmojiType(
        UUID userId, UUID messageId, EmojiType emojiType);

    // 댓글 목록용 집계 (N+1 방지)
    @Query("""
        SELECT new com.memorin.domain.emoji.dto.response.MessageEmojiCountDto(
            me.message.id, me.emojiType, COUNT(me),
            SUM(CASE WHEN me.user.id = :meId THEN 1 ELSE 0 END) > 0)
        FROM MessageEmoji me
        WHERE me.message.id IN :messageIds
        GROUP BY me.message.id, me.emojiType
        """)
    List<MessageEmojiCountDto> countByMessageIds(
        @Param("messageIds") List<UUID> messageIds,
        @Param("meId") UUID meId);
}

