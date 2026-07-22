package com.memorin.domain.post_comments.repository;

import com.memorin.domain.post_comments.entity.PostComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public interface PostCommentRepository extends JpaRepository<PostComments, UUID> {

    @Query("SELECT c FROM PostComments c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<PostComments> findActiveById(@Param("id") UUID id);

    /* TODO 댓글 목록 조회에 사용 지금은 주석 처리
    @Query("""
            SELECT c FROM PostComments c
            WHERE c.post.id = :postId AND c.deletedAt IS NULL
            ORDER BY c.createdAt ASC
            """)
    List<PostComments> findActiveByPost(@Param("postId") UUID postId);
    */

    // 게시물 상세/목록에 보여줄 "지금 이 순간"의 실제 댓글 수. asOf 필터 없음.
    @Query("SELECT COUNT(c) FROM PostComments c WHERE c.post.id = :postId AND c.deletedAt IS NULL")
    long countActiveByPostId(@Param("postId") UUID postId);

    interface PostCommentCountRow {
        UUID getPostId();
        Long getCommentCount();
    }

    @Query("""
            SELECT c.post.id AS postId, COUNT(c) AS commentCount
            FROM PostComments c
            WHERE c.post.id IN :postIds AND c.deletedAt IS NULL AND c.createdAt <= :asOf
            GROUP BY c.post.id
            """)
    List<PostCommentCountRow> countGroupedByPostIds(
            @Param("postIds") Collection<UUID> postIds,
            @Param("asOf") Instant asOf
    );

    default Map<UUID, Long> countAllByPostIdIn(Collection<UUID> postIds, Instant asOf) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return countGroupedByPostIds(postIds, asOf).stream()
                .collect(Collectors.toMap(PostCommentCountRow::getPostId, PostCommentCountRow::getCommentCount));
    }
}