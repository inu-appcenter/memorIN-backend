package com.memorin.domain.emoji.dto.response;

import com.memorin.domain.emoji.entity.EmojiType;

import java.util.UUID;

public record MessageEmojiCountDto(
    UUID messageId,
    EmojiType emojiType,
    long count,
    boolean reactedByMe) {
}
