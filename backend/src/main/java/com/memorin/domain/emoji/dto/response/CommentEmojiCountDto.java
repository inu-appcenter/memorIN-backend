package com.memorin.domain.emoji.dto.response;

import com.memorin.domain.emoji.entity.EmojiType;

import java.util.UUID;

public record CommentEmojiCountDto(
    UUID commentId,
    EmojiType emojiType,
    long count,
    boolean reactedByMe) {
}
