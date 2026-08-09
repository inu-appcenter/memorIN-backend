package com.memorin.domain.posts.repository;

import com.memorin.domain.posts.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Date;
import java.time.LocalDateTime;import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    @Query(value = """
            SELECT * FROM posts p
            WHERE p.user_id = CAST(:userId AS uuid)
              AND p.deleted_at IS NULL
              AND (:includeAllVisibility = TRUE OR p.visibility = 'PUBLIC'
              OR (p.visibility = 'FRIENDS'
              AND EXISTS (SELECT 1 FROM follows f WHERE f.status = 'ACCEPTED'
                      AND ((f.follower_id = CAST(:requesterId AS uuid) AND f.following_id = p.user_id)
                          OR (f.following_id = CAST(:requesterId AS uuid) AND f.follower_id = p.user_id))
                          )
              )
          )
              AND (
                    CAST(:cursorRecordedDate AS date) IS NULL
                    OR (p.recorded_date, p.id) < (CAST(:cursorRecordedDate AS date), CAST(:cursorId AS uuid))
                  )
            ORDER BY p.recorded_date DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Post> findUserFeed(
            @Param("userId") UUID userId,
            @Param("requesterId") UUID requesterId,
            @Param("includeAllVisibility") boolean includeAllVisibility,
            @Param("cursorRecordedDate") Date cursorRecordedDate,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit
    );

    // posts 도메인 단독 쿼리. 다른 도메인과 JOIN하지 않는다.
    @Query(value = """
        SELECT * FROM posts p
        WHERE p.deleted_at IS NULL
          AND p.visibility = 'PUBLIC'
          AND p.created_at > :windowStart
          AND p.created_at <= :asOf
        ORDER BY p.created_at DESC
        LIMIT :candidatePoolSize
        """, nativeQuery = true)
    List<Post> findRecommendationCandidates(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("asOf") LocalDateTime asOf,
            @Param("candidatePoolSize") int candidatePoolSize
    );

    @Query(value = """
        SELECT *
        FROM posts p
        WHERE p.user_id IN (:userIds)
          AND p.deleted_at IS NULL
          AND p.visibility IN ('PUBLIC','FRIENDS')
          AND (
                CAST(:cursorRecordedDate AS date) IS NULL
                OR (p.recorded_date, p.id)
                    < (CAST(:cursorRecordedDate AS date), CAST(:cursorId AS uuid))
              )
        ORDER BY p.recorded_date DESC, p.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Post> findFriendFeed(
        @Param("userIds") List<UUID> userIds,
        @Param("cursorRecordedDate") Date cursorRecordedDate,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );
}
