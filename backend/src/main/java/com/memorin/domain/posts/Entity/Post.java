package com.memorin.domain.posts.Entity;

import com.memorin.domain.users.Entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "posts")
public class Post {

    @Id
    @OneToMany(fetch = FetchType.LAZY)
    @Column(name = "id", columnDefinition = "UUID DEFAULT gen_random_uuid()", nullable = false) // DB에서 랜덤으로 UUID를 생성하도록 함.
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 N:1로 형성
    @JoinColumn(name = "user_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    private User user_id; // FK

    @JdbcTypeCode(SqlTypes.JSON) // Hibernate에서 jsonb 타입으로 매핑
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    @ColumnDefault("[]")
    private String content; // 게시물

    @Column(name = "visibility", nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("PUBLIC")
    private Visibility_type visibility; // 공개 유무(?)

    @Column(name = "recorded_date", nullable = false)
    @ColumnDefault("CURRENT_DATE") // ERD과 DDL에 따라서 CURRENT_TIMESTAMP가 아닌 CURRENT_DATE로 설정
    private Date recorded_date;

    @Column(name = "view_count", nullable = false)
    @ColumnDefault("0") // private int view_count = 0; 으로 하고 @ColumnDefault("0")를 제거해도 동일하게 작동
    private int view_count; // 조회수(?)

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime created_at; // 만들어진 날짜

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @ColumnDefault("CURRENT_TIMESTAMP") // CURRENT_DATE 사용 X -> 시/분/초 까지 저장하기 위해서
    private LocalDateTime updated_at; // 수정된 날짜

    @Column(name = "deleted_at")
    @ColumnDefault("false") // 기본 값을 null로
    private LocalDateTime deleted_at; // 삭제된 날짜

    // Builder는 작성 논의

}
