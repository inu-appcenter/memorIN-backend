package com.memorin.domain.notifications.dto;

import java.util.List;
import java.util.UUID;

public record NotificationPageResponse(
    List<NotificationResponse> items,
    UUID nextCursor,
    boolean hasNext
) {
}
