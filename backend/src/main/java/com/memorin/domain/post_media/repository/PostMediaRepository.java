package com.memorin.domain.post_media.repository;

import com.memorin.domain.post_media.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

    // 소프트 삭제된 게시물은 quota 집계에서 제외한다.
    // post_media.deleted_at은 DDL엔 있지만 어떤 코드 경로도 채우지 않는다(첨부 교체는 하드 DELETE,
    // 게시물 소프트삭제는 posts.deleted_at만 갱신) - 항상 NULL인 죽은 조건이라 제거했다.
    @Query(value = """
            SELECT COALESCE(SUM(pm.file_size_bytes), 0)
            FROM post_media pm
            JOIN posts p ON pm.post_id = p.id
            WHERE p.user_id = :userId
              AND p.deleted_at IS NULL
            """, nativeQuery = true)
    long sumFileSizeBytesByUserId(@Param("userId") UUID userId);

    List<PostMedia> findByPostIdOrderByOrderIndexAsc(UUID postId);

    // 목록(피드) 조회 시 게시물마다 따로 쿼리하지 않고 N+1 없이 한 번에 가져오기 위한 배치 조회.
    List<PostMedia> findByPostIdInOrderByOrderIndexAsc(Collection<UUID> postIds);

    @Modifying
    @Query("DELETE FROM PostMedia m WHERE m.post.id = :postId")
    void deleteAllByPostId(@Param("postId") UUID postId);
}
