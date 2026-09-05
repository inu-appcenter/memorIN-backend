package com.memorin.domain.notifications.service;

import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.web_push.service.WebPushService;
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

    @Async("notificationPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPushNotificationRequested(PushNotificationRequested event) {
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
}
