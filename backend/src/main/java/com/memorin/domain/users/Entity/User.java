package com.memorin.domain.users.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.memorin.global.support.GeneratedUuidV7;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    //로그인용 이메일
    //추후 학번으로 로그인 확정되면 추가 예정
    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email; // 유저 이메일

    @Column(name = "password_hash", nullable = false)
    private String passwordHash; // 비밀번호

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username; // 이름

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName; // 닉네임

    @Column(name = "bio")
    private String bio; // 자기소개 글

    @Column(name = "profile_image_key", length = 500)
    private String profileImageKey; // MinIO에 저장된 경로 키

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")// CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime createdAt; // 만들어진 날짜

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt; // 수정된 날짜

    @Column(name = "deleted_at")
    @ColumnDefault("false") // 기본 값을 null로
    private LocalDateTime deletedAt; // 삭제된 날짜

    //이메일 인증 기능 구현 시 추가 예정
//    @Column(name = , nullable = false)
//    private boolean emailVerified = false;

    // Builder는 작성 논의

    // 임시 AuthService Builder
    public User(String email,
                String passwordHash,
                String username,
                String displayName,
                String bio
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.username = username;
        this.displayName = displayName;
        this.bio = bio;
    }

}
