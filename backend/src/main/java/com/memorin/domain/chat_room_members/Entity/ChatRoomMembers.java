package com.memorin.domain.chat_room_members.Entity;

import com.memorin.domain.chat_rooms.Entity.ChatRooms;
import com.memorin.domain.users.Entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
    @Column(name = "id", columnDefinition = "UUID DEFAULT gen_random_uuid()", nullable = false) // DB에서 랜덤으로 UUID를 생성하도록 함.
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false) // chat_rooms 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatRooms room_id; // FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    @JoinColumn()
    private User user_id; // FK

    @Column(name ="role", nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("MEMBER")
    private Members_role role; // 채팅방 내 역할

    @Column(name = "joined_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime joined_at;

    @Column(name = "last_read_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime last_read_at;

    @Column(name = "joined_at")
    private LocalDateTime left_at;

}
