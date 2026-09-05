package com.memorin.domain.web_push.repository;

import com.memorin.domain.web_push.entity.WebPushSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, UUID> {

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    List<WebPushSubscription> findAllByUserId(UUID userId);

    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);
}
