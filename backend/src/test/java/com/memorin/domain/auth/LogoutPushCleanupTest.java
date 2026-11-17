package com.memorin.domain.auth;

import com.memorin.domain.auth.dto.LogoutRequest;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.auth.repository.RefreshTokenRepository;
import com.memorin.domain.auth.service.AuthService;
import com.memorin.domain.fcm_token.service.FcmTokenService;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.domain.web_push.service.WebPushSubscriptionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LogoutPushCleanupTest {

    private static final String SECRET = "security-test-secret-must-be-at-least-32-bytes";

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final FcmTokenService fcmTokenService = mock(FcmTokenService.class);
    private final WebPushSubscriptionService webPushSubscriptionService = mock(WebPushSubscriptionService.class);
    private final AuthService service = new AuthService(
        mock(UserRepository.class),
        mock(PasswordEncoder.class),
        new JwtTokenProvider(SECRET, 60_000, 60_000, mock(UserRepository.class)),
        refreshTokenRepository,
        fcmTokenService,
        webPushSubscriptionService, mock(org.springframework.context.ApplicationEventPublisher.class)
    );
    private final UUID userId = UUID.randomUUID();

    @Test
    void 본문_없는_로그아웃은_사용자의_푸시_등록을_전부_지운다() {
        service.logout(userId, null);

        verify(refreshTokenRepository).deleteById(userId);
        verify(fcmTokenService).deleteAll(userId);
        verify(webPushSubscriptionService).deleteAll(userId);
    }

    @Test
    void 값이_모두_비어_있으면_본문_없는_것과_같다() {
        service.logout(userId, new LogoutRequest(" ", null));

        verify(fcmTokenService).deleteAll(userId);
        verify(webPushSubscriptionService).deleteAll(userId);
        verify(fcmTokenService, never()).delete(any(), any());
    }

    @Test
    void 기기_값이_있으면_그_기기의_등록만_지운다() {
        service.logout(userId, new LogoutRequest("current-fcm-token", null));

        verify(fcmTokenService).delete(userId, "current-fcm-token");
        verify(fcmTokenService, never()).deleteAll(any());
        verify(webPushSubscriptionService, never()).deleteAll(any());
        verify(webPushSubscriptionService, never()).delete(any(), any());
    }
}
