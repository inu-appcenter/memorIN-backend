package com.memorin.domain.chat_room_members.entity;

import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.users.entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Table(
        name = "chat_room_members",
        uniqueConstraints = { // 한 사람이 같은 방에 두 번 들어오지 않도록 함.
                @UniqueConstraint(
                        name = "uq_room_member", // 제약 조건의 이름
                        columnNames = {"room_id", "user_id"} // 유니크하게 묶을 컬럼명들
)
    })
public class ChatRoomMembers {

    @Id
    @GeneratedUuidV7 // UUID 생성자 변경, UUID와 @OneToMany 혼용 불가
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false) // chat_rooms 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatRooms room; // FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    private User user; // FK

    @Column(name ="role", nullable = false)
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM) // DDL의 member_role은 Postgres 네이티브 ENUM
    @ColumnDefault("MEMBER")
    private Members_role role; // 채팅방 내 역할

    @Column(name = "joined_at", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime joinedAt;

    @Column(name = "last_read_at", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime lastReadAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

}
