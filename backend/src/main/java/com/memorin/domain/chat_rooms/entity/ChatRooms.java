package com.memorin.domain.chat_rooms.entity;

import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_rooms")
public class ChatRooms {

    @Id
    @GeneratedUuidV7 // UUID 생성자 변경, UUID와 @OneToMany 혼용 불가
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id; // PK

    @Column(name = "name", length = 100)
    private String name; // 방 이름

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM) // DDL의 chat_type은 Postgres 네이티브 ENUM
    @ColumnDefault("DIRECT")
    private Chat_type type; // 1:1 채팅 or 그룹 채팅

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz") // timestamptz로 시간 오차 발생 방어
    private LocalDateTime createdAt; // 만들어진 날짜

    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime updatedAt; // 수정된 날짜

    @Builder
    public ChatRooms(String name, Chat_type type, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.name = name;
        this.type = type;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ChatRooms createDirect() {
        return ChatRooms.builder()
            .type(Chat_type.DIRECT)
            .build();
    }

    public static ChatRooms createGroup(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("그룹 채팅방 이름은 비어 있을 수 없습니다.");
        }
        return ChatRooms.builder()
            .name(name)
            .type(Chat_type.GROUP)
            .build();
    }

    public void rename(String newName) {
        if (this.type != Chat_type.GROUP) {
            throw new IllegalStateException("1:1 채팅방은 이름을 변경할 수 없습니다.");
        }
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("방 이름은 비어 있을 수 없습니다.");
        }
        this.name = newName;
    }


}
