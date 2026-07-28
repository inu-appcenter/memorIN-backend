package com.memorin.domain.fcm_token.entity;

import com.memorin.domain.users.entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fcm_tokens",
    uniqueConstraints = { // 같은 FCM 토큰이 DB에 중복 저장되는 것을 방지
        @UniqueConstraint(
            columnNames = {"user_id", "device_type"}
        )
    }
)
public class FcmToken {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // FK
    private User user;

    @Column(nullable = false, length = 500)
    private String token;

    @Enumerated(EnumType.STRING) // enum 저장 방식 결정 (기본값: String)
    @Column(nullable = false)
    private DeviceType deviceType;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public FcmToken(User user, DeviceType deviceType, String token){
        this.user = user;
        this.deviceType = deviceType;
        this.token = token;
    }

    public void update(String token){
        this.token = token;
    }
}
