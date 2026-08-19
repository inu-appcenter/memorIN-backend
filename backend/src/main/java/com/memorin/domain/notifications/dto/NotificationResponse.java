package com.memorin.domain.notifications.dto;

import com.memorin.domain.notifications.entity.Notification;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.users.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    UUID actorId,
    String actorUsername,
    String actorDisplayName,
    String title,
    String message,
    UUID referenceId,

    boolean read,
    LocalDateTime createdAt

) {
    public static NotificationResponse from(Notification notification) {
        User actor = notification.getActor();

        UUID actorId = null;
        String actorUsername = null;
        String actorDisplayName = null;

        if (actor != null) {
            actorId = actor.getId();
            actorUsername = actor.getUsername();
            actorDisplayName = actor.getDisplayName();
        }

        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            actorId,
            actorUsername,
            actorDisplayName,
            notification.getTitle(),
            notification.getMessage(),
            notification.getReferenceId(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }
}
