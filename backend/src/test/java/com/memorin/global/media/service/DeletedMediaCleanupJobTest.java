package com.memorin.global.media.service;

import com.memorin.domain.media_deletion.entity.MediaDeletionQueue;
import com.memorin.domain.media_deletion.repository.MediaDeletionQueueRepository;
import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.global.media.MinioProperties;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 이슈 #259: 삭제된 게시물·교체된 첨부의 MinIO 오브젝트는 유예 기간 후 회수돼야 한다.
// 오브젝트 삭제는 되돌릴 수 없으므로 "지우면 안 되는 경우"를 특히 꼼꼼히 고정한다.
@ExtendWith(MockitoExtension.class)
class DeletedMediaCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration GRACE = Duration.ofDays(7);
    private static final int BATCH_SIZE = 100;

    @Mock
    private PostMediaRepository postMediaRepository;

    @Mock
    private MediaDeletionQueueRepository mediaDeletionQueueRepository;

    @Mock
    private MinioClient minioClient;

    private final MinioProperties properties = new MinioProperties(
            "http://minio:9000", "http://localhost:9000", "us-east-1",
            "key", "secret", "bucket", 600, 300, 1000L, List.of("image/png")
    );

    private DeletedMediaCleanupJob job() {
        return new DeletedMediaCleanupJob(
                postMediaRepository, mediaDeletionQueueRepository, minioClient, properties,
                GRACE, BATCH_SIZE, FIXED_CLOCK);
    }

    private PostMedia media(String fileKey) {
        return PostMedia.of(null, fileKey, "image/png", 500L, (short) 0, 100, 100);
    }

    @Test
    void 유예_기간이_지난_삭제_게시물의_오브젝트를_지우고_행도_지운다() throws Exception {
        PostMedia target = media("uploads/deleted-post.png");
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of(target));
        given(postMediaRepository.countLiveByFileKey("uploads/deleted-post.png")).willReturn(0L);

        job().cleanup();

        ArgumentCaptor<RemoveObjectArgs> removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(removed.capture());
        assertThat(removed.getValue().object()).isEqualTo("uploads/deleted-post.png");
        assertThat(removed.getValue().bucket()).isEqualTo("bucket");
        verify(postMediaRepository).delete(target);
    }

    @Test
    void 유예_기간_경계는_지금에서_유예일수를_뺀_시각이고_batch_크기만큼만_조회한다() {
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of());
        given(mediaDeletionQueueRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .willReturn(List.of());

        job().cleanup();

        LocalDateTime expectedCutoff = LocalDateTime.of(2026, 9, 14, 0, 0);
        ArgumentCaptor<Pageable> postPage = ArgumentCaptor.forClass(Pageable.class);
        verify(postMediaRepository).findMediaOfPostsDeletedBefore(org.mockito.ArgumentMatchers.eq(expectedCutoff), postPage.capture());
        assertThat(postPage.getValue().getPageSize()).isEqualTo(BATCH_SIZE);

        ArgumentCaptor<Pageable> queuePage = ArgumentCaptor.forClass(Pageable.class);
        verify(mediaDeletionQueueRepository)
                .findByCreatedAtBeforeOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(expectedCutoff), queuePage.capture());
        assertThat(queuePage.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
    }

    @Test
    void MinIO_삭제가_실패하면_행을_남겨서_다음_주기에_재시도한다() throws Exception {
        PostMedia target = media("uploads/deleted-post.png");
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of(target));
        given(postMediaRepository.countLiveByFileKey("uploads/deleted-post.png")).willReturn(0L);
        willThrow(new RuntimeException("minio down")).given(minioClient).removeObject(any(RemoveObjectArgs.class));

        job().cleanup();

        verify(postMediaRepository, never()).delete(any(PostMedia.class));
    }

    @Test
    void 한_건이_실패해도_나머지는_계속_처리한다() throws Exception {
        PostMedia failing = media("uploads/fail.png");
        PostMedia ok = media("uploads/ok.png");
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of(failing, ok));
        given(postMediaRepository.countLiveByFileKey(any())).willReturn(0L);
        willThrow(new RuntimeException("boom")).given(minioClient)
                .removeObject(org.mockito.ArgumentMatchers.argThat(a -> a != null && "uploads/fail.png".equals(a.object())));

        job().cleanup();

        verify(postMediaRepository, never()).delete(failing);
        verify(postMediaRepository).delete(ok);
    }

    @Test
    void 같은_키를_살아있는_게시물이_쓰면_오브젝트는_지우지_않고_행만_지운다() throws Exception {
        PostMedia target = media("uploads/shared.png");
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of(target));
        given(postMediaRepository.countLiveByFileKey("uploads/shared.png")).willReturn(1L);

        job().cleanup();

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(postMediaRepository).delete(target);
    }

    @Test
    void 교체로_버려진_파일도_유예_후_오브젝트와_대기열_행을_함께_지운다() throws Exception {
        MediaDeletionQueue queued = MediaDeletionQueue.of("uploads/replaced.png");
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of());
        given(mediaDeletionQueueRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .willReturn(List.of(queued));
        given(postMediaRepository.countLiveByFileKey("uploads/replaced.png")).willReturn(0L);

        job().cleanup();

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(mediaDeletionQueueRepository).delete(queued);
    }

    @Test
    void 교체_파일이_다시_살아있는_게시물에_붙어_있으면_오브젝트는_지키고_대기열_행만_지운다() throws Exception {
        MediaDeletionQueue queued = MediaDeletionQueue.of("uploads/reattached.png");
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of());
        given(mediaDeletionQueueRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .willReturn(List.of(queued));
        given(postMediaRepository.countLiveByFileKey("uploads/reattached.png")).willReturn(1L);

        job().cleanup();

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(mediaDeletionQueueRepository).delete(queued);
    }

    @Test
    void 대기열_삭제가_실패하면_대기열_행을_남긴다() throws Exception {
        MediaDeletionQueue queued = MediaDeletionQueue.of("uploads/replaced.png");
        given(postMediaRepository.findMediaOfPostsDeletedBefore(any(), any(Pageable.class))).willReturn(List.of());
        given(mediaDeletionQueueRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(any(), any(Pageable.class)))
                .willReturn(List.of(queued));
        given(postMediaRepository.countLiveByFileKey("uploads/replaced.png")).willReturn(0L);
        willThrow(new RuntimeException("minio down")).given(minioClient).removeObject(any(RemoveObjectArgs.class));

        job().cleanup();

        verify(mediaDeletionQueueRepository, never()).delete(any(MediaDeletionQueue.class));
    }
}
