package com.memorin.domain.posts.entity;

import com.memorin.domain.users.entity.User;
import com.memorin.global.support.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedUuidV7 // UUID 생성자 변경, UUID와 @OneToMany 혼용 불가
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id; // PK

    @ManyToOne(fetch = FetchType.LAZY) // FK 관계를 N:1로 형성
    @JoinColumn(name = "user_id", nullable = false) // users 도메인의 PK와 FK 관계 형성
    private User user; // FK

    @JdbcTypeCode(SqlTypes.JSON) // Hibernate에서 jsonb 타입으로 매핑
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    @ColumnDefault("[]")
    private String content; // 게시물

    // DDL의 visibility_type/timeslot_type은 Postgres 네이티브 ENUM이다.
    // NAMED_ENUM 없이 EnumType.STRING만 두면 varchar로 전송돼 INSERT가 실패한다.
    @Column(name = "visibility", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @ColumnDefault("PUBLIC")
    private VisibilityType visibility; // 공개 유무

    @Column(name = "timeslot", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TimeslotType timeslot; // 시간대

    @Column(name = "recorded_date", nullable = false)
    @ColumnDefault("CURRENT_DATE") // ERD과 DDL에 따라서 CURRENT_TIMESTAMP가 아닌 CURRENT_DATE로 설정
    private Date recordedDate;

    @Column(name = "view_count", nullable = false)
    @ColumnDefault("0") // private int viewCount = 0; 으로 하고 @ColumnDefault("0")를 제거해도 동일하게 작동
    private int viewCount; // 조회수(?)

    @CreationTimestamp // INSERT 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz") // timestamptz로 시간 오차 발생 방어
    private LocalDateTime createdAt; // 만들어진 날짜

    @UpdateTimestamp // UPDATE 시 자동으로 현재 시간을 값으로 채워서 쿼리 생성.
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime updatedAt; // 수정된 날짜

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 삭제된 날짜

    // Builder는 작성 논의

    // 임시 builder
    @Builder
    private Post(
            User user, String content, VisibilityType visibilityType, TimeslotType timeslot,
            Date recordedDate, int viewCount, LocalDateTime createdAt,
            LocalDateTime updatedAt, LocalDateTime deletedAt
    ){
        this.user = user; // 누락 시 user_id(nullable=false) 제약 위반으로 게시물 생성이 실패한다.
        this.content = content;
        this.visibility = visibilityType != null ? visibilityType : VisibilityType.PUBLIC;
        this.timeslot = timeslot;
        this.recordedDate = recordedDate;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static Post create(User user, String content, VisibilityType visibility,
                              TimeslotType timeslot, Date recordedDate) {
        return Post.builder()
                .user(user)
                .content(content)
                .visibilityType(visibility)
                .timeslot(timeslot)
                .recordedDate(recordedDate != null ? recordedDate : Date.valueOf(LocalDate.now()))
                .build();
    }

    public void update(String content, VisibilityType visibility, TimeslotType timeslot, Date recordedDate) {
        if (isDeleted()) {
            throw new IllegalStateException("삭제된 게시물은 수정할 수 없습니다.");
        }
        if (content != null) this.content = content;
        if (visibility != null) this.visibility = visibility;
        if (timeslot != null) this.timeslot = timeslot;
        if (recordedDate != null) this.recordedDate = recordedDate;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void increaseViewCount() {
        this.viewCount += 1;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.user.getId().equals(userId);
    }

}
