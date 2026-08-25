package com.memorin.domain.messages.content;

import java.util.UUID;

public record PostShareContent(UUID postId) implements MessageContent {
    @Override
    public String type() {
        return "POST_SHARE";
    }
}
