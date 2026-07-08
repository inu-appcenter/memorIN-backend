package com.memorin.domain.post_media.Entity;

import com.memorin.domain.posts.Entity.Post;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "post_media")
public class PostMedia {

    @Id
    @Column(name = "id", columnDefinition = "UUID DEFAULT gen_random_uuid()") // DB에서 랜덤으로 UUID를 생성하도록 함.
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 1:N로 형성
    @JoinColumn(name = "post_id", nullable = false) // posts 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE) // DB에 쿼리문을 직접 전달 -> 빠르고 정확함.
    private Post postId; // FK

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes")
    private long fileSizeBytes;

    @Column(name = "order_index", nullable = false)
    @ColumnDefault("0")
    private Short orderIndex;

    @Column(name = "width")
    private int width;

    @Column(name = "height")
    private int height;

    @Column(name = "duration_sec")
    private int durationSec;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")// CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime createdAt; // 만들어진 날짜

}
