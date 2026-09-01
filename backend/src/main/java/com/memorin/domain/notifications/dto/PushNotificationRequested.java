package com.memorin.domain.notifications.dto;

import com.memorin.domain.notifications.entity.NotificationType;
import java.util.UUID;

public record PushNotificationRequested(
    UUID recipientId,
    UUID actorId,
    NotificationType type,
    String title,
    String body,
    UUID referenceId
) {
}
