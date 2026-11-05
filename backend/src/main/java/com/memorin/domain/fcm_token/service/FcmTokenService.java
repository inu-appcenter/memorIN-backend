package com.memorin.domain.fcm_token.service;

import com.memorin.domain.fcm_token.dto.FcmTokenRequest;
import com.memorin.domain.fcm_token.repository.FcmTokenRepository;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenService {

    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;

    // 토큰 값 기준으로 저장한다. 같은 기기에서 다른 사용자가 로그인해 같은 토큰을 등록하면
    // 이전 사용자의 행이 새 사용자 소유로 바뀐다 — 이전 사용자의 알림이 이 기기로 가지 않는다.
    // 한 사용자의 여러 기기(같은 종류 포함)는 각자 행을 가진다.
    public void save(UUID userId, FcmTokenRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_001);
        }

        fcmTokenRepository.upsert(userId, request.token(), request.deviceType().name());
    }

    public void delete(UUID userId, String token) {
        fcmTokenRepository.deleteByUserIdAndToken(userId, token);
    }

    public void deleteAll(UUID userId) {
        fcmTokenRepository.deleteByUserId(userId);
    }
}
