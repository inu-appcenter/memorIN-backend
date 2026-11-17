package com.memorin.domain.web_push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.web_push.entity.WebPushSubscription;
import com.memorin.domain.web_push.repository.WebPushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final WebPushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Value("${web-push.enabled:false}")
    private boolean enabled;

    @Value("${web-push.vapid.public-key:}")
    private String publicKey;

    @Value("${web-push.vapid.private-key:}")
    private String privateKey;

    @Value("${web-push.vapid.subject:mailto:admin@memorin.local}")
    private String subject;

    // 푸시 서비스 한 곳이 응답하지 않을 때 알림 발송 스레드가 묶이지 않게 한다(#311).
    @Value("${web-push.send-timeout-ms:10000}")
    private long sendTimeoutMs;

    private PushService pushService;

    @PostConstruct
    void initialize() {
        if (!enabled) {
            return;
        }

        if (publicKey.isBlank() || privateKey.isBlank()) {
            throw new IllegalStateException("web-push is enabled but VAPID keys are missing");
        }

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        try {
            pushService = new PushService();
            pushService.setPublicKey(publicKey);
            pushService.setPrivateKey(privateKey);
            pushService.setSubject(subject);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Web Push VAPID configuration", e);
        }
    }

    public void send(PushNotificationRequested event) {
        if (!enabled) {
            return;
        }

        String payload = payloadOf(event);
        List<WebPushSubscription> subscriptions = subscriptionRepository.findAllByUserId(event.recipientId());
        for (WebPushSubscription subscription : subscriptions) {
            // 검증이 생기기 전에 등록된 행이 남아 있을 수 있어 보내기 직전에도 확인한다.
            if (!WebPushEndpointPolicy.isAllowed(subscription.getEndpoint())) {
                log.warn("Skipped Web Push to a disallowed endpoint. subscriptionId={}", subscription.getId());
                continue;
            }
            try {
                Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthKey(),
                    payload.getBytes(StandardCharsets.UTF_8)
                );
                HttpResponse response = sendWithTimeout(notification);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 404 || statusCode == 410) {
                    subscriptionRepository.delete(subscription);
                    log.info("Removed expired Web Push subscription. subscriptionId={}", subscription.getId());
                } else if (statusCode >= 400) {
                    log.warn("Web Push endpoint returned status {}. subscriptionId={}", statusCode, subscription.getId());
                }
            } catch (TimeoutException e) {
                log.warn("Web Push delivery timed out after {}ms. subscriptionId={}", sendTimeoutMs, subscription.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Web Push delivery interrupted. subscriptionId={}", subscription.getId());
                return;
            } catch (Exception e) {
                log.warn("Web Push delivery failed. subscriptionId={}", subscription.getId(), e);
            }
        }
    }

    // send(notification)은 sendAsync(...).get()이라 기다림에 끝이 없다. 시간 안에 안 오면 취소한다.
    // 취소하면 라이브러리의 ClosableCallback.cancelled()가 요청마다 만든 HTTP 클라이언트를 닫는다.
    // 인코딩은 send(notification)의 기본값인 AESGCM을 명시한다. sendAsync(notification)의 기본값은
    // AES128GCM이라 그대로 쓰면 보내는 형식이 바뀐다(WebPushRequestHeaderTest가 확인하는 헤더 포함).
    private HttpResponse sendWithTimeout(Notification notification) throws Exception {
        Future<HttpResponse> future = pushService.sendAsync(notification, Encoding.AESGCM);
        try {
            return future.get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    private String payloadOf(PushNotificationRequested event) {
        try {
            return objectMapper.writeValueAsString(new WebPushPayload(
                event.title(), event.body(), event.type().name(),
                event.referenceId() == null ? null : event.referenceId().toString(),
                event.postId() == null ? null : event.postId().toString(),
                event.actorId() == null ? null : event.actorId().toString()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Web Push payload", e);
        }
    }

    private record WebPushPayload(String title, String body, String type, String referenceId, String postId, String actorId) {
    }
}
