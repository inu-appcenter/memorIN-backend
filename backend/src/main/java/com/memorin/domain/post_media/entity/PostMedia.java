package com.memorin.domain.post_media.entity;

import com.memorin.domain.posts.entity.Post;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.Builder;
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
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 N:1로 형성
    @JoinColumn(name = "post_id", nullable = false) // posts 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE) // DB에 쿼리문을 직접 전달 -> 빠르고 정확함.
    // 필드는 ID가 아니라 Post 연관이므로 post로 둔다.
    // postId로 두면 PostMediaRepository의 findByPost_Id... 파생 쿼리가 속성을 찾지 못해 부팅이 실패한다.
    private Post post; // FK

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "order_index", nullable = false)
    @ColumnDefault("0")
    private Short orderIndex;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")// CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime createdAt; // 만들어진 날짜

    @Builder
    private PostMedia(
            Post post, String fileKey, String mimeType, Long fileSizeBytes,
            Short orderIndex, Integer width, Integer height
    ) {
        this.post = post;
        this.fileKey = fileKey;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.orderIndex = orderIndex;
        this.width = width;
        this.height = height;
    }

    public static PostMedia of(
            Post post, String fileKey, String mimeType, Long fileSizeBytes,
            Short orderIndex, Integer width, Integer height
    ){
        return PostMedia.builder()
                .post(post)
                .fileKey(fileKey)
                .mimeType(mimeType)
                .fileSizeBytes(fileSizeBytes)
                .orderIndex(orderIndex)
                .width(width)
                .height(height)
                .build();
    }

    public Post getPost() {
        return this.post;
    }

}
