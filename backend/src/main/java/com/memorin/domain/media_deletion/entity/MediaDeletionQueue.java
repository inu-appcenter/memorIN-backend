package com.memorin.domain.media_deletion.entity;

import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// 첨부 교체로 post_media 행이 지워질 때 버려진 MinIO 오브젝트의 키를 남겨 두는 대기열.
// 정리 배치가 유예 기간이 지난 행의 오브젝트를 지우고 이 행도 함께 지운다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "media_deletion_queue")
public class MediaDeletionQueue {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    private MediaDeletionQueue(String fileKey) {
        this.fileKey = fileKey;
    }

    public static MediaDeletionQueue of(String fileKey) {
        return new MediaDeletionQueue(fileKey);
    }
}
