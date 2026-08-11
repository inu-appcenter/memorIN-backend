package com.memorin.domain.follows.repository;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    // 팔로워 목록 (following_id = userId 인 관계들).
    // 상대(follower) 유저를 JOIN FETCH로 함께 로드 → 목록 매핑 시 유저당 SELECT N번(N+1) 방지.
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.follower
        WHERE f.following.id = :userId AND f.status = :status
        """)
    List<Follows> findByFollowingIdAndStatus(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status
    );

    // 팔로잉 목록 (follower_id = userId 인 관계들).
    // 상대(following) 유저를 JOIN FETCH로 함께 로드 → N+1 방지.
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.following
        WHERE f.follower.id = :userId AND f.status = :status
        """)
    List<Follows> findByFollowerIdAndStatus(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status
    );

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
}
