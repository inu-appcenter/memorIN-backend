package com.memorin.domain.messages.content;

public record TextContent(String text) implements MessageContent {
    @Override
    public String type() {
        return "TEXT";
    }
}
