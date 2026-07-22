package com.memorin.domain.pending_upload.entity;

import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// presigned 업로드 요청 시점의 예약. 게시물 첨부로 커밋되면 지워지고,
// 커밋 없이 expiresAt이 지나면 정리 배치가 이 행과 MinIO 오브젝트를 함께 지운다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pending_uploads")
public class PendingUpload {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    private PendingUpload(UUID userId, String objectKey, long reservedBytes, LocalDateTime expiresAt) {
        this.userId = userId;
        this.objectKey = objectKey;
        this.reservedBytes = reservedBytes;
        this.expiresAt = expiresAt;
    }

    public static PendingUpload of(UUID userId, String objectKey, long reservedBytes, LocalDateTime expiresAt) {
        return PendingUpload.builder()
                .userId(userId)
                .objectKey(objectKey)
                .reservedBytes(reservedBytes)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public boolean isOwnedBy(UUID requesterId) {
        return this.userId.equals(requesterId);
    }
}
