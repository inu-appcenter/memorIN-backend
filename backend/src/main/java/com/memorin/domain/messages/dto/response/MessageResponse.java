package com.memorin.domain.messages.dto.response;

import com.memorin.domain.messages.content.MessageContent;
import com.memorin.domain.messages.entity.MessageType;
import com.memorin.domain.messages.entity.Messages;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID roomId,
    UUID senderId,
    MessageType type,
    MessageContent content,
    LocalDateTime sentAt
) {
    public static MessageResponse of(Messages message, MessageContent parsedContent) {
        return new MessageResponse(
            message.getId(),
            message.getRoom().getId(),
            message.getSender().getId(),
            message.getType(),
            parsedContent,
            message.getSentAt()
        );
    }
}
