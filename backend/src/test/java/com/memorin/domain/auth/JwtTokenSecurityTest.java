package com.memorin.domain.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.auth.entity.RefreshToken;
import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.auth.repository.RefreshTokenRepository;
import com.memorin.domain.auth.service.AuthService;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtTokenSecurityTest {

    private static final String SECRET = "security-test-secret-must-be-at-least-32-bytes";

    @Test
    void access_and_refresh_tokens_are_distinguished_by_typ_claim() {
        JwtTokenProvider provider = provider();
        UUID userId = UUID.randomUUID();

        String accessToken = provider.createAccessToken(userId);
        String refreshToken = provider.createRefreshToken(userId);

        assertThat(claim(accessToken, "typ")).isEqualTo("access");
        assertThat(claim(refreshToken, "typ")).isEqualTo("refresh");
        assertThatCode(() -> provider.validateAccessToken(accessToken)).doesNotThrowAnyException();
        assertThatCode(() -> provider.validateRefreshToken(refreshToken)).doesNotThrowAnyException();
        assertThatThrownBy(() -> provider.validateAccessToken(refreshToken))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_004);
        assertThatThrownBy(() -> provider.validateRefreshToken(accessToken))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_004);
    }

    @Test
    void refresh_token_is_rejected_by_http_authentication_filter_with_401() throws Exception {
        JwtTokenProvider provider = provider();
        String refreshToken = provider.createRefreshToken(UUID.randomUUID());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            provider, new RestAuthenticationEntryPoint(new ObjectMapper()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void old_token_without_typ_claim_is_rejected() {
        JwtTokenProvider provider = provider();
        String oldToken = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

        assertThatThrownBy(() -> provider.validateAccessToken(oldToken))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void access_token_cannot_be_reissued_and_refresh_token_is_stored_as_a_hash() {
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000, 60_000, userRepository);
        AuthService service = new AuthService(userRepository, passwordEncoder, provider, refreshTokenRepository);
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.reissue(provider.createAccessToken(userId)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.AUTH_004);

        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(passwordEncoder.matches("password", "password-hash")).willReturn(true);
        given(user.getPasswordHash()).willReturn("password-hash");
        given(userRepository.findByEmail("user@example.com")).willReturn(java.util.Optional.of(user));
        var response = service.login(
            new com.memorin.domain.auth.dto.LoginRequest("user@example.com", "password"));

        ArgumentCaptor<RefreshToken> token = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(token.capture());
        assertThat(token.getValue().getRefreshTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(token.getValue().getRefreshTokenHash())
            .isEqualTo(provider.hashRefreshToken(response.refreshToken()));
    }

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(SECRET, 60_000, 60_000, mock(UserRepository.class));
    }

    private String claim(String token, String name) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get(name, String.class);
    }
}
