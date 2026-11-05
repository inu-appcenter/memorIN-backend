package com.memorin.domain.auth;

import com.memorin.domain.auth.dto.SignupRequest;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.auth.repository.RefreshTokenRepository;
import com.memorin.domain.auth.service.AuthService;
import com.memorin.domain.fcm_token.service.FcmTokenService;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.domain.web_push.service.WebPushSubscriptionService;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StartupHardeningTest {

    private static final String VALID_SECRET = "security-test-secret-must-be-at-least-32-bytes";

    @Test
    void example_jwt_secret_is_rejected_at_startup() {
        assertThatThrownBy(() -> provider("changeme-in-production-use-256bit-secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("예시 값")
            .hasMessageNotContaining("changeme-in-production");
    }

    @Test
    void short_or_blank_jwt_secret_is_rejected_at_startup() {
        assertThatThrownBy(() -> provider("too-short-secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageNotContaining("too-short-secret");
        assertThatThrownBy(() -> provider(""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void random_32_byte_secret_is_accepted() {
        assertThatCode(() -> provider(VALID_SECRET)).doesNotThrowAnyException();
    }

    @Test
    void signup_is_rejected_when_disabled() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthService service = authService(userRepository);
        ReflectionTestUtils.setField(service, "signupEnabled", false);

        assertThatThrownBy(() -> service.signup(request()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_005);
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_is_allowed_when_enabled() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthService service = authService(userRepository);
        ReflectionTestUtils.setField(service, "signupEnabled", true);

        service.signup(request());

        verify(userRepository).save(any());
    }

    private JwtTokenProvider provider(String secret) {
        return new JwtTokenProvider(secret, 60_000, 60_000, mock(UserRepository.class));
    }

    private AuthService authService(UserRepository userRepository) {
        return new AuthService(
            userRepository,
            mock(PasswordEncoder.class),
            provider(VALID_SECRET),
            mock(RefreshTokenRepository.class),
            mock(FcmTokenService.class),
            mock(WebPushSubscriptionService.class)
        );
    }

    private SignupRequest request() {
        return new SignupRequest("user@example.com", "password123", "daily_user", "Daily User", null);
    }
}
