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

    @Query("""
    SELECT f.following.id
    FROM Follows f
    WHERE f.follower.id = :userId
      AND f.status = com.memorin.domain.follows.entity.Follow_state.ACCEPTED
    """)
    List<UUID> findFollowingIds(UUID userId);

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

    // 팔로워 목록
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.follower
        WHERE f.following.id = :userId
        AND f.status = :status
        AND (:cursor IS NULL OR f.id < :cursor)
        ORDER BY f.id DESC
    """)
    List<Follows> findFollowersWithCursor(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status,
        @Param("cursor") UUID cursor,
        Pageable pageable
    );

    // 팔로잉 목록
    @Query("""
        SELECT f FROM Follows f
        JOIN FETCH f.following
        WHERE f.follower.id = :userId
        AND f.status = :status
        AND (:cursor IS NULL OR f.id < :cursor)
        ORDER BY f.id DESC
    """)
    List<Follows> findFollowingsWithCursor(
        @Param("userId") UUID userId,
        @Param("status") Follow_state status,
        @Param("cursor") UUID cursor,
        Pageable pageable
    );
}
