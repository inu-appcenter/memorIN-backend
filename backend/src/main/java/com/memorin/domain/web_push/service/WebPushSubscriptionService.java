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
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        WebPushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint()).orElse(null);
        if (subscription == null) {
            subscriptionRepository.save(new WebPushSubscription(user, request.endpoint(), request.p256dh(), request.auth()));
            return;
        }

        subscription.update(user, request.endpoint(), request.p256dh(), request.auth());
    }

    public void delete(UUID userId, String endpoint) {
        subscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
    }
}
