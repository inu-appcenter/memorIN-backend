package com.memorin.domain.chat_rooms.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "chat_rooms")
public class ChatRooms {

    @Id
    @OneToMany(fetch = FetchType.LAZY)
    @Column(name = "id", columnDefinition = "UUID DEFAULT gen_random_uuid()", nullable = false) // DB에서 랜덤으로 UUID를 생성하도록 함.
    private UUID id; // PK

    @Column(name = "name", length = 100)
    private String name; // 방 이름

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("DIRECT")
    private Chat_type type; // 1:1 채팅 or 그룹 채팅

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnail_key;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime created_at; // 만들어진 날짜

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP") // CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime updated_at; // 수정된 날짜


}
