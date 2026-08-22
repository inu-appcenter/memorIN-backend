package com.memorin.domain.messages.dto.request;

import java.util.UUID;

public record PostShareRequest(
    UUID roomId,
    UUID postId
) {
}
