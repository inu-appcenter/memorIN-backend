package com.memorin.domain.notifications;

import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.notifications.service.FcmPushService;
import com.memorin.domain.notifications.service.NotificationPushEventListener;
import com.memorin.domain.web_push.service.WebPushService;
import com.memorin.global.config.WebSocketSessionRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationPushEventListenerTest {

    private final FcmPushService fcmPushService = mock(FcmPushService.class);
    private final WebPushService webPushService = mock(WebPushService.class);
    private final WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
    private final NotificationPushEventListener listener =
        new NotificationPushEventListener(fcmPushService, webPushService, registry);

    private final UUID recipientId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = "MESSAGE", mode = EnumSource.Mode.EXCLUDE)
    void 소켓이_연결돼_있어도_메시지_외_알림은_항상_푸시한다(NotificationType type) {
        // 레지스트리가 어떤 답을 하든 MESSAGE가 아니면 보지 않는다.
        given(registry.isSubscribedToRoom(any(), any())).willReturn(true);
        PushNotificationRequested event = event(type, UUID.randomUUID());

        listener.onPushNotificationRequested(event);

        verify(fcmPushService).send(event);
        verify(webPushService).send(event);
    }

    @Test
    void 해당_방을_구독_중이면_메시지_푸시를_생략한다() {
        given(registry.isSubscribedToRoom(recipientId, roomId)).willReturn(true);
        PushNotificationRequested event = event(NotificationType.MESSAGE, roomId);

        listener.onPushNotificationRequested(event);

        verify(fcmPushService, never()).send(any());
        verify(webPushService, never()).send(any());
    }

    @Test
    void 해당_방을_구독하지_않았으면_소켓이_연결돼_있어도_메시지_푸시를_보낸다() {
        given(registry.isSubscribedToRoom(recipientId, roomId)).willReturn(false);
        PushNotificationRequested event = event(NotificationType.MESSAGE, roomId);

        listener.onPushNotificationRequested(event);

        verify(fcmPushService).send(event);
        verify(webPushService).send(event);
    }

    private PushNotificationRequested event(NotificationType type, UUID referenceId) {
        return new PushNotificationRequested(
            recipientId, UUID.randomUUID(), type, "title", "body", referenceId, null);
    }
}
