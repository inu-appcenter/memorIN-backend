package com.memorin.domain.post_comments.repository;

import com.memorin.domain.post_comments.entity.PostComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public interface PostCommentRepository extends JpaRepository<PostComments, UUID> {

    @Query("SELECT c FROM PostComments c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<PostComments> findActiveById(@Param("id") UUID id);


    // 스레드 전체 조회 (활성 + tombstone 포함) - 자식이 부모 없이 떠 있는 것처럼 보이지 않도록 tombstone도 함께 내려준다.
    //
    // JOIN FETCH c.user는 필수다. 응답에 작성자 닉네임·프로필이 들어가면서 c.getUser()의
    // 프록시가 초기화되는데, FETCH가 없으면 댓글 1건당 users SELECT가 1번씩 붙어 N+1이 된다.
    // (PK만 읽던 시절엔 프록시가 초기화되지 않아 안전했다 — 필드가 늘면서 조건이 바뀌었다.)
    @Query("""
        SELECT c FROM PostComments c
        JOIN FETCH c.user
        WHERE c.post.id = :postId
        ORDER BY c.createdAt ASC
        """)
    List<PostComments> findThreadByPostId(@Param("postId") UUID postId);

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
            @Param("asOf") LocalDateTime asOf
    );

    default Map<UUID, Long> countAllByPostIdIn(Collection<UUID> postIds, LocalDateTime asOf) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        return countGroupedByPostIds(postIds, asOf).stream()
                .collect(Collectors.toMap(PostCommentCountRow::getPostId, PostCommentCountRow::getCommentCount));
    }
}
