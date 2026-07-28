package com.memorin.domain.fcm_token.service;

import com.memorin.domain.fcm_token.dto.FcmTokenRequest;
import com.memorin.domain.fcm_token.entity.FcmToken;
import com.memorin.domain.fcm_token.repository.FcmTokenRepository;
import com.memorin.domain.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;

    public void save(User user, FcmTokenRequest request) {

        FcmToken token = fcmTokenRepository
            .findByUserIdAndDeviceType(
                user.getId(),
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
