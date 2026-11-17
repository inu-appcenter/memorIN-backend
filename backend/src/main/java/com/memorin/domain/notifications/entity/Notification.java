package com.memorin.domain.notifications.entity;

import com.memorin.domain.users.entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    // 알림을 받는 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 알림을 발생시킨 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    public static final int MESSAGE_MAX_LENGTH = 500;

    @Column(name = "message", nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    // 게시물, 팔로우 등 관련 데이터의 id
    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;

    // 댓글(답글 포함) 알림이 달린 게시물의 id. 그 외 타입은 null
    @Column(name = "post_id", columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "is_read", nullable = false)
    @ColumnDefault("false")
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime createdAt;

    public Notification(User user, User actor, NotificationType type, String title, String message, UUID referenceId) {
        this(user, actor, type, title, message, referenceId, null);
    }

    public Notification(User user, User actor, NotificationType type, String title, String message, UUID referenceId, UUID postId) {
        this.user = user;
        this.actor = actor;
        this.type = type;
        this.title = title;
        this.message = truncate(message, MESSAGE_MAX_LENGTH);
        this.referenceId = referenceId;
        this.postId = postId;
        this.read = false;
    }

    public void read() {
        this.read = true;
    }

    // 채팅 본문 등 길이 제한이 없는 값이 그대로 들어와도 VARCHAR(500)을 넘지 않게 자른다.
    // PostgreSQL VARCHAR(n)은 문자(code point) 수로 세므로, 이모지 같은 서로게이트 쌍이 갈라지지 않게 code point 기준으로 자른다.
    private static String truncate(String value, int maxLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxLength - 1);
        return value.substring(0, end) + "…";
    }
}
