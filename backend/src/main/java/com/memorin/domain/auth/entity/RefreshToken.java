package com.memorin.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "refresh_token", nullable = false, length = 500)
    private String refreshTokenHash;

    public RefreshToken(UUID userId, String refreshTokenHash) {
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
    }

    public void update(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }
}
