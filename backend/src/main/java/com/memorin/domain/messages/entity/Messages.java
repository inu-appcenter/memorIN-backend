package com.memorin.domain.messages.entity;

import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.users.entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "messages")
public class Messages {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false) // chat_rooms 도메인의 PK와 FK 관계 형성
    private ChatRooms room; // FK

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 1:N로 형성
    @JoinColumn(name = "sender_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    private User sender; // FK

    // 신규 필드: TEXT/IMAGE/POST_SHARE 구분용. content(jsonb)만으로는
    // 방 목록 마지막 메시지 미리보기를 만들 때마다 JSON을 파싱해야 해서 컬럼으로 분리했습니다.
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM) // DDL의 chat_type은 Postgres 네이티브 ENUM
    @ColumnDefault("TEXT")
    private MessageType type;

    @JdbcTypeCode(SqlTypes.JSON) // Hibernate에서 jsonb 타입으로 매핑
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private String content; // 게시물

    @Column(name = "sent_at", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime sentAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Messages(ChatRooms room, User sender, MessageType type, String content, LocalDateTime sentAt) {
        this.room = room;
        this.sender = sender;
        this.type = type;
        this.content = content;
        this.sentAt = sentAt;
    }

    public static Messages createPostShare(ChatRooms room, User sender, String contentJson) {
        return Messages.builder()
            .room(room)
            .sender(sender)
            .type(MessageType.POST_SHARE)
            .content(contentJson)
            .sentAt(LocalDateTime.now())
            .build();
    }

    public static Messages createText(ChatRooms room, User sender, String contentJson) {
        return Messages.builder()
            .room(room)
            .sender(sender)
            .type(MessageType.TEXT)
            .content(contentJson)
            .sentAt(LocalDateTime.now())
            .build();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

}
