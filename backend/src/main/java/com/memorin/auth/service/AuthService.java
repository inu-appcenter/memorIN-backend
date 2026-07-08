package com.memorin.auth.service;

import com.memorin.auth.dto.LoginRequest;
import com.memorin.auth.dto.LoginResponse;
import com.memorin.auth.dto.SignupRequest;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.member.entity.Member;
import com.memorin.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public void signup(SignupRequest request) {

        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.MEMBER_002);
        }

        if (memberRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.MEMBER_003);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        Member member = new Member(
                request.email(),
                passwordHash,
                request.username(),
                request.displayName()
        );

        memberRepository.save(member);
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_002));

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_002);
        }

        // JWT 구현 시 실제 Access Token 발급 후 반환 예정
        // 현재 "로그인 성공" 메세지는 임시 값
        return new LoginResponse("로그인 성공");
    }
}
