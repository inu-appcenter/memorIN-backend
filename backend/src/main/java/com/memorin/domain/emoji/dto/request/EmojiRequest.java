package com.memorin.domain.emoji.dto.request;

import com.memorin.domain.emoji.entity.EmojiType;
import jakarta.validation.constraints.NotNull;

public record EmojiRequest(
    @NotNull
    EmojiType emojiType
) {
}
