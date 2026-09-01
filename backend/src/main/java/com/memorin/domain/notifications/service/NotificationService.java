package com.memorin.domain.notifications.service;

import com.memorin.domain.notifications.dto.NotificationPageResponse;
import com.memorin.domain.notifications.dto.NotificationResponse;
import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.notifications.entity.Notification;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.notifications.repository.NotificationRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void save(UUID userId, UUID actorId, NotificationType type, String title, String message, UUID referenceId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        User actor = null;

        if (actorId != null) {
            actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        }

        Notification notification = new Notification(user, actor, type, title, message, referenceId);

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new PushNotificationRequested(
            userId, actorId, type, title, message, referenceId
        ));
    }

    public NotificationPageResponse getNotifications(UUID userId, UUID cursor, Integer size) {
        int limit = normalizeSize(size);
        Pageable pageable = PageRequest.of(0, limit + 1);
        List<Notification> notifications = notificationRepository.findNotifications(userId, cursor, pageable);
        boolean hasNext = false;

        if (notifications.size() == limit + 1) {
            hasNext = true;
            notifications.remove(limit);
        }

        List<NotificationResponse> items = new ArrayList<>();

        for (Notification notification : notifications) {
            NotificationResponse response = NotificationResponse.from(notification);
            items.add(response);
        }

        UUID nextCursor = null;

        if (hasNext && notifications.size() > 0) {
            Notification last = notifications.get(notifications.size() - 1);
            nextCursor = last.getId();
        }

        return new NotificationPageResponse(items, nextCursor, hasNext);
    }

    @Transactional
    public void read(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_001));

        notification.read();
    }

    @Transactional
    public void readAll(UUID userId) {
        notificationRepository.readAll(userId);
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }

        if (size < 1) {
            return 1;
        }

        if (size > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }

        return size;
    }
}
