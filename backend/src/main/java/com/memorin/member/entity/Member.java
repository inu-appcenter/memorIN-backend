package com.memorin.member.entity;

import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    //로그인용 이메일
    //추후 학번으로 로그인 확정되면 추가 예정
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column()
    private String bio;

    @Column(name = "profile_image_key", length = 500)
    private String profileImageKey;

    //이메일 인증 기능 구현 시 추가 예정
//    @Column(name = , nullable = false)
//    private boolean emailVerified = false;

    public Member(String email, String passwordHash, String username, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.username = username;
        this.displayName = displayName;
    }
}
