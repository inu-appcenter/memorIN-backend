package com.memorin.domain.emoji.dto.response;

import com.memorin.domain.emoji.entity.EmojiType;

public record EmojiToggleResponse(

    EmojiType emojiType,
    boolean added

) {
}
