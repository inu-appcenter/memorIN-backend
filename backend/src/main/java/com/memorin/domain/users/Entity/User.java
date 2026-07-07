package com.memorin.domain.users.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID userId; // PK

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email; // 유저 이메일

    @Column(name = "password_hash", nullable = false)
    private String password_hash; // 비밀번호

    @Column(name = "username", nullable = false, unique = true ,length = 50)
    private String username; // 이름

    @Column(name = "display_name", nullable = false, length = 100)
    private String display_name; // 닉네임

    @Column(name = "bio")
    private String bio; // 자기소개 글

    @Column(name = "profile_image_key", length = 500)
    private String profile_image_key; // MinIO에 저장된 경로 키

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")// CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime created_at; // 만들어진 날짜

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updated_at; // 수정된 날짜

    @Column(name = "deleted_at")
    @ColumnDefault("false") // 기본 값을 null로
    private LocalDateTime deleted_at; // 삭제된 날짜


    // Builder는 작성 논의


}
