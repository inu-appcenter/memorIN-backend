package com.memorin.domain.web_push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.domain.web_push.dto.WebPushSubscriptionRequest;
import com.memorin.domain.web_push.entity.WebPushSubscription;
import com.memorin.domain.web_push.repository.WebPushSubscriptionRepository;
import com.memorin.domain.web_push.service.WebPushService;
import com.memorin.domain.web_push.service.WebPushSubscriptionService;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** #311: 구독 등록의 endpoint 검증·소유권 검사와 발송 타임아웃. */
class WebPushSubscriptionGuardTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc:APA91b";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WebPushSubscriptionRepository subscriptionRepository = mock(WebPushSubscriptionRepository.class);
    private final WebPushSubscriptionService service =
        new WebPushSubscriptionService(userRepository, subscriptionRepository);

    private User user(UUID id) {
        User user = new User(id + "@memorin.test", "hash", id.toString(), "name", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void 허용되지_않은_endpoint는_DB를_보기_전에_400으로_거절한다() {
        UUID me = UUID.randomUUID();

        assertThatThrownBy(() -> service.save(me,
                new WebPushSubscriptionRequest("https://169.254.169.254/latest/meta-data/", "p", "a")))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.COMMON_002);
        verifyNoInteractions(userRepository, subscriptionRepository);
    }

    @Test
    void 다른_사용자의_endpoint는_넘기지_않고_409로_거절한다() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        WebPushSubscription existing = new WebPushSubscription(user(owner), ENDPOINT, "owner-p", "owner-a");
        given(userRepository.findById(attacker)).willReturn(Optional.of(user(attacker)));
        given(subscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.save(attacker, new WebPushSubscriptionRequest(ENDPOINT, "evil-p", "evil-a")))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.COMMON_006);

        assertThat(existing.getUser().getId()).isEqualTo(owner);
        assertThat(existing.getP256dhKey()).isEqualTo("owner-p");
        assertThat(existing.getAuthKey()).isEqualTo("owner-a");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void 내_endpoint를_다시_등록하면_키만_갱신한다() {
        UUID me = UUID.randomUUID();
        WebPushSubscription existing = new WebPushSubscription(user(me), ENDPOINT, "old-p", "old-a");
        given(userRepository.findById(me)).willReturn(Optional.of(user(me)));
        given(subscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(existing));

        service.save(me, new WebPushSubscriptionRequest(ENDPOINT, "new-p", "new-a"));

        assertThat(existing.getUser().getId()).isEqualTo(me);
        assertThat(existing.getP256dhKey()).isEqualTo("new-p");
        assertThat(existing.getAuthKey()).isEqualTo("new-a");
    }

    @Test
    void 응답하지_않는_푸시_서비스는_타임아웃_후_취소하고_다음_구독으로_넘어간다() throws Exception {
        // Notification이 구독 키를 실제로 디코딩하므로 유효한 P-256 공개키가 필요하다.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String p256dh = b64.encodeToString(((ECPublicKey) generator.generateKeyPair().getPublic()).getQ().getEncoded(false));
        String auth = b64.encodeToString(new byte[16]);

        UUID me = UUID.randomUUID();
        WebPushSubscription hanging = new WebPushSubscription(user(me), ENDPOINT, p256dh, auth);
        WebPushSubscription disallowed =
            new WebPushSubscription(user(me), "https://127.0.0.1/push", p256dh, auth);
        given(subscriptionRepository.findAllByUserId(me)).willReturn(List.of(disallowed, hanging));

        PushService pushService = mock(PushService.class);
        CompletableFuture<HttpResponse> never = new CompletableFuture<>();
        given(pushService.sendAsync(any(Notification.class), eq(Encoding.AESGCM))).willReturn(never);

        WebPushService webPushService = new WebPushService(subscriptionRepository, new ObjectMapper());
        ReflectionTestUtils.setField(webPushService, "enabled", true);
        ReflectionTestUtils.setField(webPushService, "sendTimeoutMs", 200L);
        ReflectionTestUtils.setField(webPushService, "pushService", pushService);

        PushNotificationRequested event = new PushNotificationRequested(
            me, UUID.randomUUID(), NotificationType.LIKE, "t", "b", null, null);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> webPushService.send(event));

        assertThat(never.isCancelled()).isTrue();
        // 허용되지 않은 endpoint로는 아예 보내지 않는다(검증 이전에 저장된 행).
        verify(pushService).sendAsync(any(Notification.class), eq(Encoding.AESGCM));
    }
}
