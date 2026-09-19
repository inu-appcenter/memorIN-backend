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

    public void signup(SignupRequest request) {

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
        User user = userRepository.findByEmail(request.email())
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

        if (request == null) {
            return;
        }

        if (request.fcmToken() != null && !request.fcmToken().isBlank()) {
            fcmTokenService.delete(userId, request.fcmToken());
        }

        if (request.webPushEndpoint() != null && !request.webPushEndpoint().isBlank()) {
            webPushSubscriptionService.delete(userId, request.webPushEndpoint());
        }
    }
}
