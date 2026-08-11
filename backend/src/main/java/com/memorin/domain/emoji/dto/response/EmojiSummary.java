package com.memorin.domain.emoji.dto.response;

import com.memorin.domain.emoji.entity.EmojiType;

public record EmojiSummary(

    EmojiType emojiType,
    long count,
    boolean reactedByMe

) {

    public static EmojiSummary from(EmojiCountDto d) {
        return new EmojiSummary(d.emojiType(), d.count(), d.reactedByMe());
    }

}
