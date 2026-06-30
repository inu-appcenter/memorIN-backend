package com.memorin;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;

    public FcmService(FcmTokenRepository fcmTokenRepository) {
        this.fcmTokenRepository = fcmTokenRepository;
    }

    /** 단일 유저에게 전송 (테스트/단발성 알림용) */
    public void sendMessage(String userId, String title, String body) {
        sendToUsers(List.of(userId), title, body);
    }

    /** 여러 유저(보낸 사람 제외한 방 참여자 등)에게 전송 */
    @Transactional
    public void sendToUsers(Collection<String> userIds, String title, String body) {
        System.out.println("[FCM] 수신대상 userIds=" + userIds);
        int tokenCount = 0;
        for (String userId : userIds) {
            List<FcmToken> tokens = fcmTokenRepository.findByUserId(userId);
            System.out.println("[FCM]   " + userId + " 의 토큰 수=" + tokens.size());
            for (FcmToken fcmToken : tokens) {
                send(fcmToken, title, body);
                tokenCount++;
            }
        }
        System.out.println("[FCM] 전송 시도한 토큰 총 " + tokenCount + "개");
    }

    private void send(FcmToken fcmToken, String title, String body) {
        com.google.firebase.messaging.Message fcmMessage =
                com.google.firebase.messaging.Message.builder()
                        .setToken(fcmToken.getToken())
                        .setNotification(
                                com.google.firebase.messaging.Notification.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .build()
                        )
                        .build();

        try {
            String id = FirebaseMessaging.getInstance().send(fcmMessage);
            System.out.println("[FCM]   전송 성공 messageId=" + id + " token=" + fcmToken.getToken().substring(0, 12) + "...");
        } catch (FirebaseMessagingException e) {
            // 만료/무효 토큰은 DB에서 정리해 죽은 토큰이 쌓이지 않게 함
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                fcmTokenRepository.delete(fcmToken);
                System.out.println("무효 토큰 삭제: " + fcmToken.getToken());
            } else {
                System.out.println("FCM 전송 실패: " + e.getMessage());
            }
        }
    }
}
