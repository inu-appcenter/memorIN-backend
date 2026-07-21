package com.memorin.domain.post_likes.Repository;

import com.memorin.domain.post_likes.Entity.PostLikes;
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
    @Query("SELECT COUNT(l) > 0 FROM PostLikes l WHERE l.postId.id = :postId AND l.userId.id = :userId")
    boolean existsByPostIdAndUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PostLikes l WHERE l.postId.id = :postId AND l.userId.id = :userId")
    int deleteByPostIdAndUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);

    long countByPostIdViaQuery(UUID postId); // 사용 안 함 - 아래 단건 카운트로 대체

    @Query("SELECT COUNT(l) FROM PostLikes l WHERE l.postId.id = :postId")
    long countByPostId(@Param("postId") UUID postId);

    interface PostLikeCountRow {
        UUID getPostId();
        Long getLikeCount();
    }

    @Query("""
            SELECT l.postId.id AS postId, COUNT(l) AS likeCount
            FROM PostLikes l
            WHERE l.postId.id IN :postIds
            GROUP BY l.postId.id
            """)
    List<PostLikeCountRow> countGroupedByPostIds(@Param("postIds") Collection<UUID> postIds);

    // RecommendedFeedService에서 바로 쓸 수 있도록 Map으로 변환해주는 default 메서드.
    default Map<UUID, Long> countAllByPostIdIn(Collection<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return countGroupedByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostLikeCountRow::getPostId, PostLikeCountRow::getLikeCount));
    }
}