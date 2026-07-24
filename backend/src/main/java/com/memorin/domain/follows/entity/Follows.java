package com.memorin.domain.follows.entity;

import com.memorin.domain.users.entity.User;
import com.memorin.global.support.GeneratedUuidV7;
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
@Check(constraints = "follower_id <> following_id") // follower와 following가 같은 사람일 수 없게 함(테이블 레벨 CHECK)
public class Follows {

    @Id
    @GeneratedUuidV7
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 1:N로 형성
    @JoinColumn(name = "follower_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User follower; // FK

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 1:N로 형성
    @JoinColumn(name = "following_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User following; // FK

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM) // DDL의 follow_status는 Postgres 네이티브 ENUM
    @ColumnDefault("PENDING")
    private Follow_state status; // 팔로우 상태

    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz") // timestamptz로 시간 오차 발생 방어
    private LocalDateTime createdAt; // 만들어진 날짜

    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime updatedAt; // 수정된 날짜

}
