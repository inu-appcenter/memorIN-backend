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
    // 토큰 하나 = 앱 설치 하나 = 소유자 한 명. 다른 사용자가 같은 토큰을 등록하면 소유권이 넘어간다(V19).
    uniqueConstraints = @UniqueConstraint(name = "uq_fcm_tokens_token", columnNames = "token"),
    indexes = @Index(name = "idx_fcm_tokens_user_id", columnList = "user_id")
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
}
