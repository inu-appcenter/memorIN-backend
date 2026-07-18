package com.memorin.domain.posts.Repository;

import com.memorin.domain.posts.Entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    @Query(value = """
            SELECT * FROM posts p
            WHERE p.user_id = CAST(:userId AS uuid)
              AND p.deleted_at IS NULL
              AND (:includeAllVisibility = TRUE OR p.visibility = 'PUBLIC')
              AND (
                    CAST(:cursorRecordedDate AS date) IS NULL
                    OR (p.recorded_date, p.id) < (CAST(:cursorRecordedDate AS date), CAST(:cursorId AS uuid))
                  )
            ORDER BY p.recorded_date DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Post> findUserFeed(
            @Param("userId") String userId,
            @Param("includeAllVisibility") boolean includeAllVisibility,
            @Param("cursorRecordedDate") Date cursorRecordedDate,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

}
