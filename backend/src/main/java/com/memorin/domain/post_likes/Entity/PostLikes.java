package com.memorin.domain.post_likes.Entity;

import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.users.Entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "post_likes")
public class PostLikes {

    @Id
    @OneToMany(fetch = FetchType.LAZY)
    @Column(name = "id", columnDefinition = "UUID DEFAULT gen_random_uuid()", nullable = false)
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false) // posts 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE) // post가 사라지면 Like도 삭제
    private Post post_id; // FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE) // user가 사라지면 Like도 삭제
    private User user_id; // FK

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")// CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime created_at; // 만들어진 날짜


}
