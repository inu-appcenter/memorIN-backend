package com.memorin.domain.post_likes.repository;

import com.memorin.domain.post_likes.entity.PostLikes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public interface PostLikeRepository extends JpaRepository<PostLikes, UUID> {

    // 필드명이 post_id/user_id라 파생 쿼리 이름 대신 명시적 JPQL을 쓴다 (위 설명 참고).
    @Query("SELECT COUNT(l) > 0 FROM PostLikes l WHERE l.post.id = :postId AND l.user.id = :userId")
    boolean existsByPostIdAndUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PostLikes l WHERE l.post.id = :postId AND l.user.id = :userId")
    void deleteByPostIdAndUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(l) FROM PostLikes l WHERE l.post.id = :postId")
    long countByPostId(@Param("postId") UUID postId);

    interface PostLikeCountRow {
        UUID getPostId();
        Long getLikeCount();
    }

    @Query("""
            SELECT l.post.id AS postId, COUNT(l) AS likeCount
            FROM PostLikes l
            WHERE l.post.id IN :postIds
            GROUP BY l.post.id
            """)
    List<PostLikeCountRow> countGroupedByPostIds(@Param("postIds") Collection<UUID> postIds);

    // RecommendedFeedService에서 바로 쓸 수 있도록 Map으로 변환해주는 default 메서드.
    default Map<UUID, Long> countAllByPostIdIn(Collection<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return countGroupedByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostLikeCountRow::getPostId, PostLikeCountRow::getLikeCount));
    }
}