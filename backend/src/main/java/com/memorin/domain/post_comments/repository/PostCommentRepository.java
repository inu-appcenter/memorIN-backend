package com.memorin.domain.post_comments.Repository;

import com.memorin.domain.post_comments.Entity.PostComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public interface PostCommentRepository extends JpaRepository<PostComments, UUID> {

    @Query("SELECT c FROM PostComments c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<PostComments> findActiveById(@Param("id") UUID id);

    @Query("""
            SELECT c FROM PostComments c
            WHERE c.post.id = :postId AND c.deletedAt IS NULL
            ORDER BY c.createdAt ASC
            """)
    List<PostComments> findActiveByPost(@Param("postId") UUID postId);

    interface PostCommentCountRow {
        UUID getPostId();
        Long getCommentCount();
    }

    @Query("""
            SELECT c.post.id AS postId, COUNT(c) AS commentCount
            FROM PostComments c
            WHERE c.post.id IN :postIds AND c.deletedAt IS NULL
            GROUP BY c.post.id
            """)
    List<PostCommentCountRow> countGroupedByPostIds(@Param("postIds") Collection<UUID> postIds);

    default Map<UUID, Long> countAllByPostIdIn(Collection<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return countGroupedByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostCommentCountRow::getPostId, PostCommentCountRow::getCommentCount));
    }
}