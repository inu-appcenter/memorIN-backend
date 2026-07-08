package com.memorin.domain.follows.Entity;

import com.memorin.domain.users.Entity.User;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "follows",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_follows", // 제약 조건의 이름
                        columnNames = {"follower_id", "following_id"} // 유니크하게 묶을 컬럼명들
                )
        })
public class Follows {

    @Id
    @Column(name = "id", columnDefinition = "UUID DEFAULT gen_random_uuid()", nullable = false) // DB에서 랜덤으로 UUID를 생성하도록 함.
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 1:N로 형성
    @JoinColumn(name = "follower_id", columnDefinition = "BIGINT CHECK (follower_id <> following_id)", nullable = false) // users 도메인의 PK와 FK 관계 형성, follower와 following가 같은 사람일 수 없게 함.
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User follower_id; // FK

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 1:N로 형성
    @JoinColumn(name = "following_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User following_id; // FK

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("PENDING")
    private Follow_state status; // 팔로우 상태

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime created_at; // 만들어진 날짜

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP") // CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime updated_at; // 수정된 날짜

}
