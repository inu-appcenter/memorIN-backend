package com.memorin.domain.follows.repository;

import com.memorin.domain.follows.Entity.Follows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follows, UUID> {

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    Optional<Follows> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);
}
