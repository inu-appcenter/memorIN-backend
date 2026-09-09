package com.memorin.domain.notifications;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.memorin.domain.fcm_token.entity.DeviceType;
import com.memorin.domain.fcm_token.entity.FcmToken;
import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.follows.service.FollowService;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.notifications.repository.NotificationRepository;
import com.memorin.domain.notifications.service.FcmPushService;
import com.memorin.domain.post_comments.repository.PostCommentRepository;
import com.memorin.domain.post_comments.service.PostCommentService;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.web_push.entity.WebPushSubscription;
import com.memorin.domain.web_push.service.WebPushService;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "firebase.enabled=false",
    "web-push.enabled=false",
    "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationPushIntegrationTest.TestAsyncConfig.class)
class NotificationPushIntegrationTest extends PostgresTestSupport {

    @TestConfiguration
    static class TestAsyncConfig {

        @Bean("notificationPushExecutor")
        Executor notificationPushExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    private FollowService followService;

    @Autowired
    private PostCommentService postCommentService;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FcmPushService fcmPushService;

    @Autowired
    private WebPushService webPushService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void disableExternalDelivery() {
        ReflectionTestUtils.setField(fcmPushService, "enabled", false);
        ReflectionTestUtils.setField(webPushService, "enabled", false);
        ReflectionTestUtils.setField(webPushService, "pushService", null);
    }

    @Test
    void followRequest_createsNotification_andAttemptsFcmAndWebPushForRegisteredRecipient()
        throws Exception {

        UUID[] ids = transactionTemplate.execute(status -> {
            User follower = persistUser("follower-" + suffix());
            User recipient = persistUser("following-" + suffix());

            entityManager.persist(
                new FcmToken(
                    recipient,
                    DeviceType.ANDROID,
                    "fcm-" + suffix()
                )
            );

            entityManager.persist(
                new WebPushSubscription(
                    recipient,
                    "https://push.test/" + suffix(),
                    "p256dh",
                    "auth"
                )
            );

            entityManager.flush();

            return new UUID[]{
                follower.getId(),
                recipient.getId()
            };
        });

        PushService pushService = enableWebPushWithSuccessfulResponse();

        ReflectionTestUtils.setField(
            fcmPushService,
            "enabled",
            true
        );

        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);

        ArgumentCaptor<Message> fcmMessage = ArgumentCaptor.forClass(Message.class);

        try (
            MockedStatic<FirebaseMessaging> firebase = mockStatic(FirebaseMessaging.class)
        ) {
            firebase.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            followService.request(ids[0], ids[1]);
            verify(firebaseMessaging).send(fcmMessage.capture());
            verify(pushService).send(any(nl.martijndwars.webpush.Notification.class));
        }

        Follows follow = followRepository.findByFollowerIdAndFollowingId(ids[0], ids[1]).orElseThrow();

        assertNotification(
            ids[1],
            NotificationType.FOLLOW_REQUEST,
            follow.getId()
        );
    }

    @Test
    void followAcceptance_createsNotification_evenWhenRecipientHasNoTokenOrWebPushSubscription() {

        UUID[] ids = transactionTemplate.execute(status -> {
            User follower = persistUser("requester-" + suffix());
            User following = persistUser("acceptor-" + suffix());

            Follows follow = new Follows(follower, following);

            entityManager.persist(follow);
            entityManager.flush();

            return new UUID[]{
                follower.getId(),
                following.getId(),
                follow.getId()
            };
        });

        enableNoDeviceDelivery();

        assertThatCode(() -> followService.accept(ids[2], ids[1])).doesNotThrowAnyException();

        assertThat(
            followRepository
                .findById(ids[2])
                .orElseThrow()
                .getStatus()
        ).isEqualTo(Follow_state.ACCEPTED);

        assertNotification(
            ids[0],
            NotificationType.FOLLOW_ACCEPTED,
            ids[2]
        );
    }

    @Test
    void commentCreation_createsNotification_andSucceedsWhenPostOwnerHasNoTokenOrWebPushSubscription() {

        UUID[] ids = transactionTemplate.execute(status -> {
            User owner = persistUser("post-owner-" + suffix());
            User author = persistUser("commenter-" + suffix());

            Post post = Post.create(
                owner,
                "[{\"type\":\"text\",\"text\":\"post\"}]",
                VisibilityType.PUBLIC,
                TimeslotType.AM,
                Date.valueOf(LocalDate.of(2026, 9, 1)),
                java.util.List.of()
            );

            entityManager.persist(post);
            entityManager.flush();

            return new UUID[]{
                owner.getId(),
                author.getId(),
                post.getId()
            };
        });

        enableNoDeviceDelivery();

        assertThatCode(
            () -> postCommentService.create(
                ids[2],
                ids[1],
                null,
                "A comment"
            )
        ).doesNotThrowAnyException();

        UUID commentId = postCommentRepository
            .findThreadByPostId(ids[2])
            .get(0)
            .getId();

        assertNotification(
            ids[0],
            NotificationType.COMMENT,
            commentId
        );
    }

    private PushService enableWebPushWithSuccessfulResponse()
        throws Exception {

        PushService pushService = mock(PushService.class);
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);

        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(201);

        when(
            pushService.send(
                any(nl.martijndwars.webpush.Notification.class)
            )
        ).thenReturn(response);

        ReflectionTestUtils.setField(
            webPushService,
            "enabled",
            true
        );

        ReflectionTestUtils.setField(
            webPushService,
            "pushService",
            pushService
        );

        return pushService;
    }

    private void enableNoDeviceDelivery() {
        ReflectionTestUtils.setField(
            fcmPushService,
            "enabled",
            true
        );

        ReflectionTestUtils.setField(
            webPushService,
            "enabled",
            true
        );

        ReflectionTestUtils.setField(
            webPushService,
            "pushService",
            mock(PushService.class)
        );
    }

    private void assertNotification(
        UUID recipientId,
        NotificationType type,
        UUID referenceId
    ) {
        assertThat(
            notificationRepository.findNotifications(
                recipientId,
                null,
                org.springframework.data.domain.PageRequest.of(0, 1)
            )
        )
            .singleElement()
            .satisfies(notification -> {
                assertThat(notification.getType()).isEqualTo(type);
                assertThat(notification.getReferenceId()).isEqualTo(referenceId);
                assertThat(notification.isRead()).isFalse();
            });
    }

    private User persistUser(String tag) {
        User user = new User(
            tag + "@memorin.test",
            "hash",
            tag,
            tag,
            null
        );

        entityManager.persist(user);

        return user;
    }

    private String suffix() {
        return UUID.randomUUID()
            .toString()
            .substring(0, 8);
    }
}
