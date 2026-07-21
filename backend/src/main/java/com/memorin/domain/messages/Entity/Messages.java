package com.memorin.domain.messages.Entity;

import com.memorin.domain.chat_rooms.Entity.ChatRooms;
import com.memorin.domain.users.Entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
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
    private ChatRooms room_id; // FK

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 1:N로 형성
    @JoinColumn(name = "sender_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    private User sender_id; // FK

    @JdbcTypeCode(SqlTypes.JSON) // Hibernate에서 jsonb 타입으로 매핑
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private String content; // 게시물

    @Column(name = "sent_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime sent_at;

    @Column(name = "deleted_at")
    private LocalDateTime deleted_at;

}
