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

    @Query("SELECT c FROM PostComments c WHERE c.id = :id AND c.deleted_at IS NULL")
    Optional<PostComments> findActiveById(@Param("id") UUID id);

    @Query("""
            SELECT c FROM PostComments c
            WHERE c.post.id = :postId AND c.deleted_at IS NULL
            ORDER BY c.created_at ASC
            """)
    List<PostComments> findActiveByPostId(@Param("postId") UUID postId);

    interface PostCommentCountRow {
        UUID getPost();
        Long getCommentCount();
    }

    @Query("""
            SELECT c.post.id AS postId, COUNT(c) AS commentCount
            FROM PostComments c
            WHERE c.post.id IN :postIds AND c.deleted_at IS NULL
            GROUP BY c.post.id
            """)
    List<PostCommentCountRow> countGroupedByPostIds(@Param("postIds") Collection<UUID> postIds);

    default Map<UUID, Long> countAllByPostIdIn(Collection<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return countGroupedByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostCommentCountRow::getPost, PostCommentCountRow::getCommentCount));
    }
}