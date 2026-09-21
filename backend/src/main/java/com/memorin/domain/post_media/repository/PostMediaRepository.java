package com.memorin.domain.post_media.repository;

import com.memorin.domain.post_media.entity.PostMedia;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // 정리 배치 대상: 소프트 삭제된 지 cutoff보다 오래된 게시물의 미디어. 오래된 순으로 batch 크기만큼.
    // 배치가 행을 지우면 다음 주기엔 이 조회에 걸리지 않으므로 알아서 끝난다.
    @Query("""
            SELECT m FROM PostMedia m JOIN m.post p
            WHERE p.deletedAt IS NOT NULL AND p.deletedAt < :cutoff
            ORDER BY p.deletedAt ASC
            """)
    List<PostMedia> findMediaOfPostsDeletedBefore(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    // 같은 file_key를 아직 살아 있는 게시물이 쓰고 있으면 오브젝트를 지우면 안 된다.
    @Query("""
            SELECT COUNT(m) FROM PostMedia m JOIN m.post p
            WHERE m.fileKey = :fileKey AND p.deletedAt IS NULL
            """)
    long countLiveByFileKey(@Param("fileKey") String fileKey);
}
