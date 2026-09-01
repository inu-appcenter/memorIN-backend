package com.memorin.domain.notifications.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.memorin.domain.fcm_token.entity.DeviceType;
import com.memorin.domain.fcm_token.entity.FcmToken;
import com.memorin.domain.fcm_token.repository.FcmTokenRepository;
import com.memorin.domain.notifications.dto.PushNotificationRequested;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final FcmTokenRepository fcmTokenRepository;

    @Value("${firebase.enabled:false}")
    private boolean enabled;

    public void send(PushNotificationRequested event) {
        if (!enabled) {
            return;
        }

        List<FcmToken> tokens = fcmTokenRepository.findAllByUserIdAndDeviceTypeIn(
            event.recipientId(),
            List.of(DeviceType.ANDROID, DeviceType.IOS)
        );

        for (FcmToken fcmToken : tokens) {
            try {
                Message message = Message.builder()
                    .setToken(fcmToken.getToken())
                    .setNotification(Notification.builder()
                        .setTitle(event.title())
                        .setBody(event.body())
                        .build())
                    .putData("type", event.type().name())
                    .putData("referenceId", event.referenceId() == null ? "" : event.referenceId().toString())
                    .putData("actorId", event.actorId() == null ? "" : event.actorId().toString())
                    .build();

                FirebaseMessaging.getInstance().send(message);
            } catch (FirebaseMessagingException e) {
                log.warn("FCM 발송 실패. tokenId={}, code={}",
                    fcmToken.getId(), e.getMessagingErrorCode(), e);

                // 앱 삭제·토큰 만료 등 더 이상 유효하지 않은 토큰 제거
                if (e.getMessagingErrorCode() != null) {
                    switch (e.getMessagingErrorCode()) {
                        case UNREGISTERED, INVALID_ARGUMENT -> fcmTokenRepository.delete(fcmToken);
                        default -> { }
                    }
                }
            }
        }
    }
}
