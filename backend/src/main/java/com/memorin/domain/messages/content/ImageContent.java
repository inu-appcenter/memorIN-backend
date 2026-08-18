package com.memorin.domain.messages.content;

public record ImageContent(String imageKey) implements MessageContent {
    @Override
    public String type() {
        return "IMAGE";
    }
}
