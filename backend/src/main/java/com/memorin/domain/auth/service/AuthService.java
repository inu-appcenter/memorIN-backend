package com.memorin.domain.auth.service;

import com.memorin.domain.auth.dto.LoginRequest;
import com.memorin.domain.auth.dto.LoginResponse;
import com.memorin.domain.auth.dto.SignupRequest;
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

    public void signup(SignupRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.MEMBER_002);
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.MEMBER_003);
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

        // JWT 구현 시 실제 Access Token 발급 후 반환 예정
        // 현재 "로그인 성공" 메세지는 임시 값
        return new LoginResponse("로그인 성공");
    }
}
