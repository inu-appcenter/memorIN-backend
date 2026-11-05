package com.memorin.domain.auth.service;

import com.memorin.domain.auth.dto.LoginRequest;
import com.memorin.domain.auth.dto.LoginResponse;
import com.memorin.domain.auth.dto.LogoutRequest;
import com.memorin.domain.auth.dto.SignupRequest;
import com.memorin.domain.auth.entity.RefreshToken;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.auth.repository.RefreshTokenRepository;
import com.memorin.domain.fcm_token.service.FcmTokenService;
import com.memorin.domain.web_push.service.WebPushSubscriptionService;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FcmTokenService fcmTokenService;
    private final WebPushSubscriptionService webPushSubscriptionService;

    // SIGNUP_ENABLED=false면 가입을 막는다. /api/health의 signupEnabled와 같은 값이다.
    @Value("${signup.enabled:true}")
    private boolean signupEnabled = true;

    public void signup(SignupRequest request) {

        if (!signupEnabled) {
            throw new BusinessException(ErrorCode.AUTH_005);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.USER_002);
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USER_003);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                request.email(),
                passwordHash,
                request.username(),
                request.displayName(),
                request.bio()
        );

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_002));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_002);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(new RefreshToken(user.getId(), jwtTokenProvider.hashRefreshToken(refreshToken)));

        return new LoginResponse(accessToken, refreshToken);
    }

    @Transactional
    public LoginResponse reissue(String refreshToken) {

        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }

        UUID userId = jwtTokenProvider.getUserId(refreshToken);

        if (userRepository.findByIdAndDeletedAtIsNull(userId).isEmpty()) {
            refreshTokenRepository.deleteById(userId);
            throw new BusinessException(ErrorCode.AUTH_003);
        }

        RefreshToken savedToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_003));

        if (!MessageDigest.isEqual(
                jwtTokenProvider.hashRefreshToken(refreshToken).getBytes(StandardCharsets.UTF_8),
                savedToken.getRefreshTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        savedToken.update(jwtTokenProvider.hashRefreshToken(newRefreshToken));

        return new LoginResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(UUID userId, LogoutRequest request) {
        refreshTokenRepository.deleteById(userId);

        boolean hasFcmToken = request != null && hasText(request.fcmToken());
        boolean hasWebPushEndpoint = request != null && hasText(request.webPushEndpoint());

        // 요청 기기를 특정할 수 없으면(본문 없음·빈 값) 사용자의 푸시 등록을 전부 지운다.
        // 남겨 두면 로그아웃한 기기로 이 사용자의 알림(채팅 본문 포함)이 계속 간다.
        // Refresh Token이 사용자당 하나라 로그아웃은 이미 모든 기기의 세션을 끝내므로, 다른 기기의
        // 등록도 함께 지우는 것이 세션 상태와 맞다. 다른 기기는 다시 로그인할 때 토큰을 재등록한다.
        if (!hasFcmToken && !hasWebPushEndpoint) {
            fcmTokenService.deleteAll(userId);
            webPushSubscriptionService.deleteAll(userId);
            return;
        }

        if (hasFcmToken) {
            fcmTokenService.delete(userId, request.fcmToken());
        }

        if (hasWebPushEndpoint) {
            webPushSubscriptionService.delete(userId, request.webPushEndpoint());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
