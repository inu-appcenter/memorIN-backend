package com.memorin.domain.post_media.repository;

import com.memorin.domain.post_media.Entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

    // 소프트 삭제된 게시물/미디어는 quota 집계에서 제외 (post_media.deleted_at, posts.deleted_at 모두 NULL만 대상)
    @Query(value = """
            SELECT COALESCE(SUM(pm.file_size_bytes), 0)
            FROM post_media pm
            JOIN posts p ON pm.post_id = p.id
            WHERE p.user_id = :userId
              AND p.deleted_at IS NULL
              AND pm.deleted_at IS NULL
            """, nativeQuery = true)
    long sumFileSizeBytesByUserId(@Param("userId") UUID userId);
}
