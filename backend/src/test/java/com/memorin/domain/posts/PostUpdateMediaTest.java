package com.memorin.domain.posts;

import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.posts.dto.request.PostCreateRequest;
import com.memorin.domain.posts.dto.request.PostUpdateRequest;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.users.entity.User;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.media.exception.UploadReservationInvalidException;
import com.memorin.global.media.service.MediaUploadCommitService;
import com.memorin.support.PostgresTestSupport;
import io.minio.MinioClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 이슈 #338: 게시물 수정에서 기존 첨부의 fileKey를 다시 보내면 예약이 이미 소비돼 commitUpload가 실패하고
// 수정 전체가 실패하던 문제. 기존 키는 그대로 두고 새 키만 커밋하는지 실제 Postgres에서 검증한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostUpdateMediaTest extends PostgresTestSupport {

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private MediaUploadCommitService mediaUploadCommitService;

    @Autowired
    private PostService postService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private record Seeded(UUID userId, UUID postId, String keyA, String keyB) {}

    // 첨부 두 개(A, B 순서)가 붙은 게시물을 만든다.
    private Seeded seed() {
        return tx.execute(status -> {
            String tag = "u" + UUID.randomUUID().toString().substring(0, 8);
            User author = new User(tag + "@memorin.test", "hash", tag, tag, null);
            em.persist(author);
            Post post = Post.create(author, "[]", VisibilityType.PUBLIC, TimeslotType.AM,
                    Date.valueOf(LocalDate.of(2026, 7, 1)), List.of(TagType.ETC));
            em.persist(post);
            String keyA = "uploads/a-" + UUID.randomUUID() + ".png";
            String keyB = "uploads/b-" + UUID.randomUUID() + ".jpg";
            em.persist(PostMedia.of(post, keyA, "image/png", 1000L, (short) 0, 10, 10));
            em.persist(PostMedia.of(post, keyB, "image/jpeg", 2000L, (short) 1, 20, 20));
            em.flush();
            return new Seeded(author.getId(), post.getId(), keyA, keyB);
        });
    }

    private PostUpdateRequest attachments(String... keys) {
        List<PostCreateRequest.AttachmentRequest> list = java.util.Arrays.stream(keys)
                .map(k -> new PostCreateRequest.AttachmentRequest(k, "image/png", 1L, 1, 1))
                .toList();
        return new PostUpdateRequest(null, null, null, null, list);
    }

    private List<PostMedia> mediaOf(UUID postId) {
        return tx.execute(status -> em.createQuery(
                        "SELECT m FROM PostMedia m WHERE m.post.id = :id ORDER BY m.orderIndex", PostMedia.class)
                .setParameter("id", postId).getResultList());
    }

    private long queueRowCount(String fileKey) {
        return tx.execute(status -> em.createQuery(
                        "SELECT COUNT(q) FROM MediaDeletionQueue q WHERE q.fileKey = :k", Long.class)
                .setParameter("k", fileKey).getSingleResult());
    }

    @Test
    void 사진_하나만_빼면_남은_첨부는_다시_커밋하지_않고_빠진_키만_대기열에_넣는다() {
        Seeded s = seed();
        UUID keptId = mediaOf(s.postId()).get(0).getId();

        postService.update(s.postId(), s.userId(), attachments(s.keyA()));

        List<PostMedia> media = mediaOf(s.postId());
        assertThat(media).extracting(PostMedia::getFileKey).containsExactly(s.keyA());
        assertThat(media.get(0).getId()).isEqualTo(keptId);
        assertThat(media.get(0).getFileSizeBytes()).isEqualTo(1000L);
        assertThat(queueRowCount(s.keyB())).isEqualTo(1);
        assertThat(queueRowCount(s.keyA())).isZero();
        verify(mediaUploadCommitService, never()).commitUpload(any(), any());
    }

    @Test
    void 순서만_바꾸면_커밋도_삭제도_없이_순서만_바뀐다() {
        Seeded s = seed();

        postService.update(s.postId(), s.userId(), attachments(s.keyB(), s.keyA()));

        List<PostMedia> media = mediaOf(s.postId());
        assertThat(media).extracting(PostMedia::getFileKey).containsExactly(s.keyB(), s.keyA());
        assertThat(media).extracting(PostMedia::getOrderIndex).containsExactly((short) 0, (short) 1);
        // 저장된 크기·mimeType은 클라이언트가 다시 보낸 값으로 바뀌지 않는다.
        assertThat(media).extracting(PostMedia::getMimeType).containsExactly("image/jpeg", "image/png");
        assertThat(media).extracting(PostMedia::getFileSizeBytes).containsExactly(2000L, 1000L);
        assertThat(queueRowCount(s.keyA())).isZero();
        assertThat(queueRowCount(s.keyB())).isZero();
        verify(mediaUploadCommitService, never()).commitUpload(any(), any());
    }

    @Test
    void 새_사진을_추가하면_새_키만_커밋한다() {
        Seeded s = seed();
        String newKey = "uploads/new-" + UUID.randomUUID() + ".png";
        given(mediaUploadCommitService.commitUpload(eq(s.userId()), eq(newKey))).willReturn(3000L);

        postService.update(s.postId(), s.userId(), attachments(s.keyA(), newKey, s.keyB()));

        List<PostMedia> media = mediaOf(s.postId());
        assertThat(media).extracting(PostMedia::getFileKey).containsExactly(s.keyA(), newKey, s.keyB());
        assertThat(media.get(1).getFileSizeBytes()).isEqualTo(3000L);
        verify(mediaUploadCommitService).commitUpload(s.userId(), newKey);
        verify(mediaUploadCommitService, never()).commitUpload(any(), eq(s.keyA()));
        verify(mediaUploadCommitService, never()).commitUpload(any(), eq(s.keyB()));
    }

    @Test
    void 다른_게시물의_키를_섞어_보내면_커밋_검증에서_거절되고_기존_첨부는_그대로다() {
        Seeded mine = seed();
        Seeded other = seed();
        given(mediaUploadCommitService.commitUpload(eq(mine.userId()), eq(other.keyA())))
                .willThrow(new UploadReservationInvalidException(other.keyA()));

        assertThatThrownBy(() -> postService.update(mine.postId(), mine.userId(), attachments(other.keyA())))
                .isInstanceOf(UploadReservationInvalidException.class);

        assertThat(mediaOf(mine.postId())).extracting(PostMedia::getFileKey)
                .containsExactly(mine.keyA(), mine.keyB());
        assertThat(queueRowCount(mine.keyA())).isZero();
        assertThat(queueRowCount(mine.keyB())).isZero();
    }

    @Test
    void 같은_키를_두_번_보내면_거절한다() {
        Seeded s = seed();

        assertThatThrownBy(() -> postService.update(s.postId(), s.userId(), attachments(s.keyA(), s.keyA())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_002);

        assertThat(mediaOf(s.postId())).hasSize(2);
    }

    @Test
    void 빈_배열이면_모든_첨부를_지우고_대기열에_넣는다() {
        Seeded s = seed();

        postService.update(s.postId(), s.userId(), new PostUpdateRequest(null, null, null, null, List.of()));

        assertThat(mediaOf(s.postId())).isEmpty();
        assertThat(queueRowCount(s.keyA())).isEqualTo(1);
        assertThat(queueRowCount(s.keyB())).isEqualTo(1);
    }
}
