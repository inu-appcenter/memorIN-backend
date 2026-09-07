package com.memorin.domain.messages.dto.response;

import java.util.List;
import java.util.UUID;

public record MessagePageResponse(
    List<MessageResponse> items,
    UUID nextCursor,
    boolean hasNext
) {
}
