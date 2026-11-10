package com.memorin.domain.web_push.service;

import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.domain.web_push.dto.WebPushSubscriptionRequest;
import com.memorin.domain.web_push.entity.WebPushSubscription;
import com.memorin.domain.web_push.repository.WebPushSubscriptionRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WebPushSubscriptionService {

    private final UserRepository userRepository;
    private final WebPushSubscriptionRepository subscriptionRepository;

    public void save(UUID userId, WebPushSubscriptionRequest request) {
        WebPushEndpointPolicy.validate(request.endpoint());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        WebPushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint()).orElse(null);
        if (subscription == null) {
            subscriptionRepository.save(new WebPushSubscription(user, request.endpoint(), request.p256dh(), request.auth()));
            return;
        }

        // 다른 사용자의 구독이면 넘기지 않는다(#311). endpoint만 알면 남의 푸시를 가로챌 수 있었다.
        // 같은 브라우저를 쓰던 사용자가 로그아웃하면 구독이 지워지므로(AuthService.logout) 정상 흐름에서는 걸리지 않는다.
        if (!subscription.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.COMMON_006, "다른 사용자가 이미 등록한 Web Push 구독입니다.");
        }

        subscription.update(user, request.endpoint(), request.p256dh(), request.auth());
    }

    public void delete(UUID userId, String endpoint) {
        subscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    public void deleteAll(UUID userId) {
        subscriptionRepository.deleteByUserId(userId);
    }
}
