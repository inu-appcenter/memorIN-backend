package com.memorin.domain.notifications.service;

import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.web_push.service.WebPushService;
import com.memorin.global.config.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushEventListener {

    private final FcmPushService fcmPushService;
    private final WebPushService webPushService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    @Async("notificationPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPushNotificationRequested(PushNotificationRequested event) {
        if (isViewingRoom(event)) {
            log.debug("Skipping MESSAGE push: recipient is subscribed to the room. recipientId={}, roomId={}",
                event.recipientId(), event.referenceId());
            return;
        }

        try {
            fcmPushService.send(event);
        } catch (RuntimeException e) {
            log.error("FCM push dispatch failed. recipientId={}", event.recipientId(), e);
        }

        try {
            webPushService.send(event);
        } catch (RuntimeException e) {
            log.error("Web Push dispatch failed. recipientId={}", event.recipientId(), e);
        }
    }

    // 푸시 생략은 MESSAGE 알림이고, 수신자가 그 채팅방 토픽을 구독 중일 때뿐이다.
    // 방 화면에서 이미 메시지를 실시간으로 받고 있으니 같은 내용을 푸시로 또 보낼 필요가 없다.
    // 좋아요·댓글·팔로우는 소켓으로 전달하는 목적지가 없으므로 연결 여부와 관계없이 항상 보낸다.
    private boolean isViewingRoom(PushNotificationRequested event) {
        return event.type() == NotificationType.MESSAGE
            && webSocketSessionRegistry.isSubscribedToRoom(event.recipientId(), event.referenceId());
    }
}
