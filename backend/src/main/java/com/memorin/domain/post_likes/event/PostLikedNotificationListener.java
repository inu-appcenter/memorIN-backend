package com.memorin.domain.post_likes.event;

import com.memorin.domain.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class PostLikedNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLiked event) {
        try {
            notificationService.saveLikeIfAbsent(
                event.recipientId(),
                event.actorId(),
                event.actorDisplayName(),
                event.postId()
            );
        } catch (DataIntegrityViolationException e) {
            log.debug("Like notification already exists. recipientId={}, actorId={}, postId={}",
                event.recipientId(), event.actorId(), event.postId());
        }
    }
}
