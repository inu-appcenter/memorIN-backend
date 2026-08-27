package com.memorin.domain.messages.dto.request;

import java.util.UUID;

public record TextRequest(
    UUID roomId,
    String text
) {
}
