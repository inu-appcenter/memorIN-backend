package com.memorin.domain.follows.repository;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follows, UUID> {

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    Optional<Follows> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    Optional<Follows> findByIdAndStatus(UUID id, Follow_state status);

    // 팔로워 목록
    List<Follows> findByFollowingIdAndStatus(
        UUID userId,
        Follow_state status
    );

    // 팔로잉 목록
    List<Follows> findByFollowerIdAndStatus(
        UUID userId,
        Follow_state status
    );
}
