package com.memorin.domain.follows.repository;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follows, UUID> {

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    Optional<Follows> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingIdAndStatus(UUID followerId, UUID followingId, Follow_state status);

    boolean existsByFollowingIdAndFollowerIdAndStatus(UUID followingId, UUID followerId, Follow_state status);

    Optional<Follows> findByIdAndStatus(UUID id, Follow_state status);

    // 페이징 없는 findByFollowingIdAndStatus / findByFollowerIdAndStatus 가 여기 있었으나,
    // 커서 페이징 버전(아래)이 들어오면서 호출부가 하나도 없는 죽은 메서드가 되어 삭제했다.
    // 목록 조회는 반드시 아래 커서 버전을 쓴다 — 페이징 없는 버전은 팔로워 수만큼 전부 로드한다.

    // status는 반드시 바인딩 파라미터로 받는다. JPQL에 enum 상수를 인라인으로 쓰면
    // Hibernate가 'ACCEPTED'::Follow_state 처럼 자바 enum 클래스명으로 캐스팅을 붙이는데,
    // 실제 Postgres 타입명은 follow_status라 type "follow_state" does not exist로 전면 실패한다.
    // (이 리포지토리의 다른 메서드들이 이미 status를 파라미터로 받는 것과 같은 방식)
    @Query("""
    SELECT f.following.id
    FROM Follows f
    WHERE f.follower.id = :userId
      AND f.status = :status
    """)
    List<UUID> findFollowingIds(@Param("userId") UUID userId, @Param("status") Follow_state status);

    // 받은 팔로우 요청 조회
    // following_id = 로그인 사용자
    // follower = 요청 보낸 사용자
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.follower
        WHERE f.following.id = :userId
        AND f.status = :status
        ORDER BY f.id DESC
    """)
    List<Follows> findReceivedRequests(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status
    );

    // 팔로워/팔로잉 목록은 "1페이지"와 "커서 이후"를 별도 쿼리로 나눠 둔다.
    //
    // 하나로 합쳐 (:cursor IS NULL OR f.id < :cursor) 로 쓰면 안 된다.
    // Hibernate는 :cursor를 바인드 파라미터($3)로 보내는데, 그러면 Postgres는 플랜을 짤 때
    // $3이 NULL인지 알 수 없어 OR을 접지 못한다. 결과적으로 커서 조건이 Index Cond가 아니라
    // Filter로 밀려나고, 인덱스로 건너뛸 수 있었을 행을 전부 읽고 나서 버린다.
    //
    // 실측 (팔로워 5만 명, 25,000번째 페이지, idx_follows_following_id 적용 상태):
    //   OR 형태  → 1.973ms · 575 buffers · Rows Removed by Filter: 25,001
    //   분리 형태 → 0.079ms ·  88 buffers · Index Cond에 id < $3 포함
    // 즉 인덱스만 넣고 OR을 그대로 두면 깊은 페이지에서 이득의 상당 부분을 잃는다.
    //
    // 인덱스는 V6__follows_cursor_pagination_index.sql 의 (following_id, status, id DESC).

    // 팔로워 목록 — 1페이지
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.follower
        WHERE f.following.id = :userId
        AND f.status = :status
        ORDER BY f.id DESC
    """)
    List<Follows> findFollowersFirstPage(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status,
        Pageable pageable
    );

    // 팔로워 목록 — 커서 이후
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.follower
        WHERE f.following.id = :userId
        AND f.status = :status
        AND f.id < :cursor
        ORDER BY f.id DESC
    """)
    List<Follows> findFollowersAfterCursor(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status,
        @Param("cursor") UUID cursor,
        Pageable pageable
    );

    // 팔로잉 목록 — 1페이지
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.following
        WHERE f.follower.id = :userId
        AND f.status = :status
        ORDER BY f.id DESC
    """)
    List<Follows> findFollowingsFirstPage(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status,
        Pageable pageable
    );

    // 팔로잉 목록 — 커서 이후
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.following
        WHERE f.follower.id = :userId
        AND f.status = :status
        AND f.id < :cursor
        ORDER BY f.id DESC
    """)
    List<Follows> findFollowingsAfterCursor(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status,
        @Param("cursor") UUID cursor,
        Pageable pageable
    );
}
