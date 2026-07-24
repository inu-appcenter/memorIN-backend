package com.memorin.domain.post_likes.entity;

import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.users.entity.User;
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
@Table(
        name = "post_likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_post_like", // 제약 조건의 이름
                        columnNames = {"post_id", "user_id"} // 유니크하게 묶을 컬럼명들
                )
        })
public class PostLikes {

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
    @OnDelete(action = OnDeleteAction.CASCADE) // user가 사라지면 Like도 삭제
    private User user; // FK

    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz") // timestamptz로 시간 오차 발생 방어
    private LocalDateTime createdAt; // 만들어진 날짜

    public static PostLikes of(Post post, User user) {
        PostLikes like = new PostLikes();
        like.post = post;
        like.user = user;
        return like;
    }
}
