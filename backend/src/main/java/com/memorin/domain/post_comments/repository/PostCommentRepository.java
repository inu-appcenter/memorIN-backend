package com.memorin.domain.post_comments.repository;

import com.memorin.domain.post_comments.entity.PostComments;
import org.springframework.data.domain.Pageable;
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


    // ── 댓글 스레드 커서 페이징 ────────────────────────────────────────────────
    //
    // 페이징 단위는 "최상위 댓글"이다. 평면 목록을 그대로 자르면 페이지 경계에서 부모와 대댓글이
    // 갈라져 FE가 트리를 그릴 수 없다. 그래서 최상위 댓글의 id만 먼저 한 페이지 뽑고(아래 두 메서드),
    // 그 id들로 본문을 한 번에 가져온다(findThreadByRootIds).
    //
    // 정렬·커서 기준은 createdAt이 아니라 id다. 댓글 id는 UUIDv7이고 createdAt은 같은 호출에서
    // LocalDateTime.now()로 박히므로 둘의 순서가 같다. 하나로 줄이면 커서에 값 하나만 담으면 되고,
    // (post_id, id) 인덱스 하나로 필터와 정렬이 같이 해결된다.
    //
    // 1페이지용과 커서용을 나눠 둔 것은 의도다 — docs/n+1-audit.md §6-4:
    // (:cursor IS NULL OR c.id > :cursor)로 합치면 깊은 페이지에서 인덱스 이득의 상당 부분을 잃는다.

    @Query("""
        SELECT c.id FROM PostComments c
        WHERE c.post.id = :postId AND c.parent IS NULL
        ORDER BY c.id ASC
        """)
    List<UUID> findRootCommentIdsFirstPage(@Param("postId") UUID postId, Pageable pageable);

    @Query("""
        SELECT c.id FROM PostComments c
        WHERE c.post.id = :postId AND c.parent IS NULL AND c.id > :cursor
        ORDER BY c.id ASC
        """)
    List<UUID> findRootCommentIdsAfter(@Param("postId") UUID postId,
                                       @Param("cursor") UUID cursor,
                                       Pageable pageable);

    // 최상위 댓글 + 그 대댓글을 한 번에. (활성 + tombstone 포함 - 자식이 부모 없이 떠 있는 것처럼
    // 보이지 않도록 tombstone도 함께 내려준다.)
    //
    // JOIN FETCH c.user는 필수다. 응답에 작성자 닉네임·프로필이 들어가면서 c.getUser()의
    // 프록시가 초기화되는데, FETCH가 없으면 댓글 1건당 users SELECT가 1번씩 붙어 N+1이 된다.
    // (PK만 읽던 시절엔 프록시가 초기화되지 않아 안전했다 — 필드가 늘면서 조건이 바뀌었다.)
    @Query("""
        SELECT c FROM PostComments c
        JOIN FETCH c.user
        WHERE c.id IN :rootIds OR c.parent.id IN :rootIds
        ORDER BY c.id ASC
        """)
    List<PostComments> findThreadByRootIds(@Param("rootIds") List<UUID> rootIds);

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
