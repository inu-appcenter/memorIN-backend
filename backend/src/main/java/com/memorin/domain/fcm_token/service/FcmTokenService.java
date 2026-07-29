package com.memorin.domain.fcm_token.service;

import com.memorin.domain.fcm_token.dto.FcmTokenRequest;
import com.memorin.domain.fcm_token.entity.FcmToken;
import com.memorin.domain.fcm_token.repository.FcmTokenRepository;
import com.memorin.domain.users.entity.User;
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

    public void save(UUID userId, FcmTokenRequest request) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        FcmToken token = fcmTokenRepository.findByUserIdAndDeviceType(
                userId,
                request.deviceType()
            )
            .orElse(null);

        if (token == null) {
            fcmTokenRepository.save(
                new FcmToken(
                    user,
                    request.deviceType(),
                    request.token()
                )
            );

            return;
        }

        token.update(request.token());
    }
}
