package com.memorin.domain.auth.service;

import com.memorin.domain.auth.dto.LoginRequest;
import com.memorin.domain.auth.dto.LoginResponse;
import com.memorin.domain.auth.dto.SignupRequest;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.domain.users.Entity.User;
import com.memorin.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

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
                request.displayName()
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

        return new LoginResponse(accessToken);
    }
}
