package com.memorin.domain.notifications;

import com.memorin.domain.notifications.dto.PushNotificationRequested;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.notifications.repository.NotificationRepository;
import com.memorin.domain.notifications.service.FcmPushService;
import com.memorin.domain.notifications.service.NotificationService;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.web_push.service.WebPushService;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
    "firebase.enabled=false",
    "web-push.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationPushIntegrationTest extends PostgresTestSupport {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private FcmPushService fcmPushService;

    @MockitoBean
    private WebPushService webPushService;

    @Test
    void savesNotification_thenDispatchesIdenticalEventToFcmAndWebPushAfterCommit() {
        UUID[] userIds = transactionTemplate.execute(status -> {
            User recipient = persistUser("push-recipient-" + randomSuffix());
            User actor = persistUser("push-actor-" + randomSuffix());
            entityManager.flush();
            return new UUID[]{recipient.getId(), actor.getId()};
        });
        UUID referenceId = UUID.randomUUID();

        notificationService.save(
            userIds[0], userIds[1], NotificationType.COMMENT,
            "New comment", "Someone commented on your post.", referenceId
        );

        ArgumentCaptor<PushNotificationRequested> fcmEvent = ArgumentCaptor.forClass(PushNotificationRequested.class);
        ArgumentCaptor<PushNotificationRequested> webPushEvent = ArgumentCaptor.forClass(PushNotificationRequested.class);

        verify(fcmPushService, timeout(5_000)).send(fcmEvent.capture());
        verify(webPushService, timeout(5_000)).send(webPushEvent.capture());

        PushNotificationRequested expected = new PushNotificationRequested(
            userIds[0], userIds[1], NotificationType.COMMENT,
            "New comment", "Someone commented on your post.", referenceId
        );
        assertThat(fcmEvent.getValue()).isEqualTo(expected);
        assertThat(webPushEvent.getValue()).isEqualTo(expected);
        assertThat(notificationRepository.findNotifications(userIds[0], null, org.springframework.data.domain.PageRequest.of(0, 1)))
            .singleElement()
            .satisfies(notification -> {
                assertThat(notification.getType()).isEqualTo(NotificationType.COMMENT);
                assertThat(notification.getTitle()).isEqualTo("New comment");
                assertThat(notification.getMessage()).isEqualTo("Someone commented on your post.");
                assertThat(notification.getReferenceId()).isEqualTo(referenceId);
                assertThat(notification.isRead()).isFalse();
            });
    }

    private User persistUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        entityManager.persist(user);
        return user;
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
