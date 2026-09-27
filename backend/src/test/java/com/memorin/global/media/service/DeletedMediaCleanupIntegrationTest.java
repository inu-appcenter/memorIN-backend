package com.memorin.global.media.service;

import com.memorin.domain.media_deletion.entity.MediaDeletionQueue;
import com.memorin.domain.media_deletion.repository.MediaDeletionQueueRepository;
import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.domain.posts.dto.request.PostCreateRequest;
import com.memorin.domain.posts.dto.request.PostUpdateRequest;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

// 이슈 #259: 실제 Postgres에서 (1) Flyway V15 + 엔티티 validate, (2) 정리 대상 조회 JPQL,
// (3) 첨부 교체 → 삭제 대기열 → 유예 후 회수 흐름을 검증한다.
// MinIO만 목으로 바꾸고 나머지는 운영과 같은 빈/스키마를 쓴다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeletedMediaCleanupIntegrationTest extends PostgresTestSupport {

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private MediaUploadCommitService mediaUploadCommitService;

    @Autowired
    private DeletedMediaCleanupJob job;

    @Autowired
    private PostService postService;

    @Autowired
    private PostMediaRepository postMediaRepository;

    @Autowired
    private MediaDeletionQueueRepository mediaDeletionQueueRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private record Seeded(UUID userId, UUID postId) {}

    // daysSinceDeleted가 null이면 살아 있는 게시물. 값이 있으면 그만큼 전에 소프트 삭제된 게시물.
    private Seeded seedPost(String fileKey, Integer daysSinceDeleted) {
        return tx.execute(status -> {
            String tag = "u" + UUID.randomUUID().toString().substring(0, 8);
            User author = new User(tag + "@memorin.test", "hash", tag, tag, null);
            em.persist(author);
            Post post = Post.create(author, "[]", VisibilityType.PUBLIC, TimeslotType.AM,
                    Date.valueOf(LocalDate.of(2026, 7, 1)), List.of(TagType.ETC));
            em.persist(post);
            em.persist(PostMedia.of(post, fileKey, "image/png", 1000L, (short) 0, 100, 100));
            em.flush();
            if (daysSinceDeleted != null) {
                em.createNativeQuery("UPDATE posts SET deleted_at = now() - (:d * interval '1 day') WHERE id = :id")
                        .setParameter("d", daysSinceDeleted)
                        .setParameter("id", post.getId())
                        .executeUpdate();
            }
            return new Seeded(author.getId(), post.getId());
        });
    }

    private void seedQueueEntry(String fileKey, int daysAgo) {
        tx.executeWithoutResult(status -> {
            MediaDeletionQueue entry = MediaDeletionQueue.of(fileKey);
            em.persist(entry);
            em.flush();
            em.createNativeQuery("UPDATE media_deletion_queue SET created_at = now() - (:d * interval '1 day') WHERE id = :id")
                    .setParameter("d", daysAgo)
                    .setParameter("id", entry.getId())
                    .executeUpdate();
        });
    }

    private long mediaRowCount(String fileKey) {
        return tx.execute(status -> em.createQuery(
                        "SELECT COUNT(m) FROM PostMedia m WHERE m.fileKey = :k", Long.class)
                .setParameter("k", fileKey).getSingleResult());
    }

    private long queueRowCount(String fileKey) {
        return tx.execute(status -> em.createQuery(
                        "SELECT COUNT(q) FROM MediaDeletionQueue q WHERE q.fileKey = :k", Long.class)
                .setParameter("k", fileKey).getSingleResult());
    }

    private List<String> removedKeys() throws Exception {
        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, atLeast(0)).removeObject(captor.capture());
        return captor.getAllValues().stream().map(RemoveObjectArgs::object).toList();
    }

    @Test
    void 유예_기간이_지난_삭제_게시물의_미디어만_회수한다() throws Exception {
        String id = UUID.randomUUID().toString();
        String oldKey = "uploads/old-" + id + ".png";
        String recentKey = "uploads/recent-" + id + ".png";
        String liveKey = "uploads/live-" + id + ".png";
        seedPost(oldKey, 10);     // 10일 전 삭제 → 유예(7일) 지남
        seedPost(recentKey, 1);   // 1일 전 삭제 → 유예 안 지남
        seedPost(liveKey, null);  // 살아 있는 게시물

        job.cleanup();

        assertThat(removedKeys()).contains(oldKey).doesNotContain(recentKey, liveKey);
        assertThat(mediaRowCount(oldKey)).isZero();
        assertThat(mediaRowCount(recentKey)).isEqualTo(1);
        assertThat(mediaRowCount(liveKey)).isEqualTo(1);
    }

    @Test
    void 첨부를_교체하면_이전_키가_삭제_대기열에_남고_유예_후_회수된다() throws Exception {
        String id = UUID.randomUUID().toString();
        String oldKey = "uploads/before-" + id + ".png";
        String newKey = "uploads/after-" + id + ".png";
        Seeded seeded = seedPost(oldKey, null);
        given(mediaUploadCommitService.commitUpload(eq(seeded.userId()), eq(newKey))).willReturn(1000L);

        postService.update(seeded.postId(), seeded.userId(), new PostUpdateRequest(
                null, null, null, null,
                List.of(new PostCreateRequest.AttachmentRequest(newKey, "image/png", 1000L, 10, 10))));

        // 교체 직후: 게시물엔 새 키만 남고, 이전 키는 대기열에 있다. 아직 유예 전이라 오브젝트는 그대로.
        assertThat(mediaRowCount(oldKey)).isZero();
        assertThat(mediaRowCount(newKey)).isEqualTo(1);
        assertThat(queueRowCount(oldKey)).isEqualTo(1);
        job.cleanup();
        assertThat(removedKeys()).doesNotContain(oldKey);
        assertThat(queueRowCount(oldKey)).isEqualTo(1);

        // 유예 기간이 지나면 회수되고 대기열 행도 사라진다. 새 키의 오브젝트는 건드리지 않는다.
        tx.executeWithoutResult(status -> em.createNativeQuery(
                        "UPDATE media_deletion_queue SET created_at = now() - interval '10 days' WHERE file_key = :k")
                .setParameter("k", oldKey).executeUpdate());
        job.cleanup();

        assertThat(removedKeys()).contains(oldKey).doesNotContain(newKey);
        assertThat(queueRowCount(oldKey)).isZero();
        assertThat(mediaRowCount(newKey)).isEqualTo(1);
    }

    @Test
    void 대기열의_키를_살아있는_게시물이_쓰고_있으면_오브젝트는_지키고_대기열_행만_지운다() throws Exception {
        String key = "uploads/shared-" + UUID.randomUUID() + ".png";
        seedPost(key, null);
        seedQueueEntry(key, 10);

        job.cleanup();

        assertThat(removedKeys()).doesNotContain(key);
        assertThat(queueRowCount(key)).isZero();
        assertThat(mediaRowCount(key)).isEqualTo(1);
        assertThat(postMediaRepository.countLiveByFileKey(key)).isEqualTo(1);
    }

    @Test
    void 유예가_안_지난_대기열_행은_건드리지_않는다() throws Exception {
        String key = "uploads/fresh-" + UUID.randomUUID() + ".png";
        seedQueueEntry(key, 1);

        job.cleanup();

        assertThat(removedKeys()).doesNotContain(key);
        assertThat(queueRowCount(key)).isEqualTo(1);
        assertThat(mediaDeletionQueueRepository.count()).isGreaterThanOrEqualTo(1);
        verify(mediaUploadCommitService, atLeast(0)).commitUpload(any(), any());
    }
}
