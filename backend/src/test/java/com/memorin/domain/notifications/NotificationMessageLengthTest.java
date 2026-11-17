package com.memorin.domain.notifications;

import com.memorin.domain.notifications.entity.Notification;
import com.memorin.domain.notifications.entity.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessageLengthTest {

    @Test
    void messageLongerThanColumn_isTruncatedTo500CodePoints() {
        String text = "가".repeat(501);

        Notification notification = new Notification(null, null, NotificationType.MESSAGE, "새 메시지", text, null);

        String message = notification.getMessage();
        assertThat(message.codePointCount(0, message.length())).isEqualTo(Notification.MESSAGE_MAX_LENGTH);
        assertThat(message).endsWith("…");
    }

    @Test
    void surrogatePairs_areNotSplit() {
        String text = "😀".repeat(600);

        Notification notification = new Notification(null, null, NotificationType.MESSAGE, "새 메시지", text, null);

        String message = notification.getMessage();
        assertThat(message.codePointCount(0, message.length())).isEqualTo(Notification.MESSAGE_MAX_LENGTH);
        assertThat(message.substring(0, message.length() - 1)).matches("(😀)+");
    }

    @Test
    void messageWithinLimit_isKeptAsIs() {
        String text = "가".repeat(500);

        Notification notification = new Notification(null, null, NotificationType.MESSAGE, "새 메시지", text, null);

        assertThat(notification.getMessage()).isEqualTo(text);
    }
}
