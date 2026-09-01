package com.memorin.domain.web_push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.web_push.entity.WebPushSubscription;
import com.memorin.domain.web_push.repository.WebPushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private PushService pushService;

    @PostConstruct
    void initialize() {
        if (!enabled) {
            return;
        }

        if (publicKey.isBlank() || privateKey.isBlank()) {
            throw new IllegalStateException("web-push is enabled but VAPID keys are missing");
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
            try {
                Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthKey(),
                    payload.getBytes(StandardCharsets.UTF_8)
                );
                HttpResponse response = pushService.send(notification);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 404 || statusCode == 410) {
                    subscriptionRepository.delete(subscription);
                    log.info("Removed expired Web Push subscription. subscriptionId={}", subscription.getId());
                } else if (statusCode >= 400) {
                    log.warn("Web Push endpoint returned status {}. subscriptionId={}", statusCode, subscription.getId());
                }
            } catch (Exception e) {
                log.warn("Web Push delivery failed. subscriptionId={}", subscription.getId(), e);
            }
        }
    }

    private String payloadOf(PushNotificationRequested event) {
        try {
            return objectMapper.writeValueAsString(new WebPushPayload(
                event.title(), event.body(), event.type().name(),
                event.referenceId() == null ? null : event.referenceId().toString(),
                event.actorId() == null ? null : event.actorId().toString()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Web Push payload", e);
        }
    }

    private record WebPushPayload(String title, String body, String type, String referenceId, String actorId) {
    }
}
