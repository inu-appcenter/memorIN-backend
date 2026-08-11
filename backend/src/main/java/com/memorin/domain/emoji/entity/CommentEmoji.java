package com.memorin.domain.emoji.entity;


import com.memorin.domain.post_comments.entity.PostComments;
import com.memorin.domain.users.entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comment_emoji",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_comment_emoji",
        columnNames = {"user_id", "comment_id", "emoji_type"} // 중복 없이 여러 이모지 가능.
    ))
public class CommentEmoji {

    @Id
    @GeneratedUuidV7 // UUID 생성자 변경, UUID와 @OneToMany 혼용 불가
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PostComments postComments; // FK

    @Enumerated(EnumType.STRING)
    @Column(name = "emoji_type", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM) // DDL의 emoji_type은 Postgres 네이티브 ENUM
    private EmojiType emojiType; // 이모지 타입 종류

    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz") // timestamptz로 시간 오차 발생 방어
    private LocalDateTime createdAt; // 만들어진 날짜

    // Hard 삭제가 효율에 좋을 것 같아 제거는 Hard로
    // Update는 삭제 후 다른 이모지 선택으로 사용자 부담 Update 형식으로 설계

    public static CommentEmoji of(User user, PostComments comment, EmojiType type) {
        CommentEmoji e = new CommentEmoji();
        e.user = user;
        e.postComments = comment;
        e.emojiType = type;
        return e;
    }
}
