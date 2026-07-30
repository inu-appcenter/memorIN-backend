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
}
