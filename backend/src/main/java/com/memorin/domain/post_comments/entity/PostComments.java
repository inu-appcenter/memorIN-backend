package com.memorin.domain.post_comments.Entity;

import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.users.Entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "post_comments")
public class PostComments {

    @Id
    @GeneratedUuidV7 // UUID 생성자 변경, UUID와 @OneToMany 혼용 불가
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false) // posts 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE) // post가 사라지면 Like도 삭제
    private Post post; // FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    private User user; // FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id") // post_comments(self) 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE) // comment가 사라지면 Like도 삭제
    private PostComments parent; // FK

    @Column(name = "body", nullable = false)
    private String body; // comment 내용

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt; // 만들어진 날짜

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt; // 수정된 날짜

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 삭제된 날짜

    // 최상위 댓글은 parent에 null을 넘긴다.
    public static PostComments of(Post post, User author, PostComments parent, String body) {
        PostComments comment = new PostComments();
        comment.post = post;
        comment.user = author;
        comment.parent = parent;
        comment.body = body;
        return comment;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

}
