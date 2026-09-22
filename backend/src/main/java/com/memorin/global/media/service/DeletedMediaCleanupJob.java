package com.memorin.global.media.service;

import com.memorin.domain.media_deletion.entity.MediaDeletionQueue;
import com.memorin.domain.media_deletion.repository.MediaDeletionQueueRepository;
import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.global.media.MinioProperties;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 삭제된 게시물·교체된 첨부의 MinIO 오브젝트를 유예 기간 후 회수한다 (이슈 #259).
// quota 집계는 삭제 즉시 사용량에서 빼지만 파일은 버킷에 남아, 올렸다 지우기를 반복하면
// quota 한도를 우회해 디스크를 채울 수 있었다.
//
// 대상은 두 가지다.
//  1) 소프트 삭제된 게시물의 post_media (posts.deleted_at이 유예 기간을 넘김)
//  2) 첨부 교체로 하드 삭제된 파일 (media_deletion_queue에 키를 남겨 둠)
//
// 안전 규칙
//  - MinIO 삭제에 성공한 뒤에만 DB 행을 지운다. 실패하면 행이 남아 다음 주기에 재시도한다.
//    (removeObject는 이미 없는 오브젝트에도 성공하므로 재시도는 멱등이다.)
//  - 같은 file_key를 살아 있는 게시물이 쓰고 있으면 오브젝트는 건드리지 않고 행만 지운다.
//  - 한 번에 batch-size개까지만 처리한다.
@Component
@ConditionalOnProperty(name = "storage.media-gc.enabled", havingValue = "true", matchIfMissing = true)
public class DeletedMediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DeletedMediaCleanupJob.class);

    private final PostMediaRepository postMediaRepository;
    private final MediaDeletionQueueRepository mediaDeletionQueueRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final Duration gracePeriod;
    private final int batchSize;
    private final Clock clock;

    @Autowired
    public DeletedMediaCleanupJob(
            PostMediaRepository postMediaRepository,
            MediaDeletionQueueRepository mediaDeletionQueueRepository,
            @Qualifier("minioClient") MinioClient minioClient,
            MinioProperties minioProperties,
            @Value("${storage.media-gc.grace-days:7}") long graceDays,
            @Value("${storage.media-gc.batch-size:100}") int batchSize
    ) {
        // posts.deleted_at은 LocalDateTime.now()(시스템 기본 시간대)로 채워지므로 같은 기준으로 비교한다.
        this(postMediaRepository, mediaDeletionQueueRepository, minioClient, minioProperties,
                Duration.ofDays(graceDays), batchSize, Clock.systemDefaultZone());
    }

    DeletedMediaCleanupJob(
            PostMediaRepository postMediaRepository,
            MediaDeletionQueueRepository mediaDeletionQueueRepository,
            MinioClient minioClient,
            MinioProperties minioProperties,
            Duration gracePeriod,
            int batchSize,
            Clock clock
    ) {
        this.postMediaRepository = postMediaRepository;
        this.mediaDeletionQueueRepository = mediaDeletionQueueRepository;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.gracePeriod = gracePeriod;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${storage.media-gc.interval-ms:3600000}")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(gracePeriod);
        cleanupDeletedPostMedia(cutoff);
        cleanupReplacedMedia(cutoff);
    }

    private void cleanupDeletedPostMedia(LocalDateTime cutoff) {
        List<PostMedia> targets = postMediaRepository.findMediaOfPostsDeletedBefore(cutoff, PageRequest.of(0, batchSize));
        for (PostMedia media : targets) {
            if (removeObjectUnlessInUse(media.getFileKey())) {
                postMediaRepository.delete(media);
            }
        }
    }

    private void cleanupReplacedMedia(LocalDateTime cutoff) {
        List<MediaDeletionQueue> targets = mediaDeletionQueueRepository
                .findByCreatedAtBeforeOrderByCreatedAtAsc(cutoff, PageRequest.of(0, batchSize));
        for (MediaDeletionQueue entry : targets) {
            if (removeObjectUnlessInUse(entry.getFileKey())) {
                mediaDeletionQueueRepository.delete(entry);
            }
        }
    }

    // DB 행을 지워도 되는 상태(오브젝트가 없어졌거나, 다른 곳에서 쓰는 중이라 건드리지 않음)면 true.
    private boolean removeObjectUnlessInUse(String fileKey) {
        if (postMediaRepository.countLiveByFileKey(fileKey) > 0) {
            log.info("Skip removing MinIO object still referenced by a live post. fileKey={}", fileKey);
            return true;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.bucketName())
                            .object(fileKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.warn("Failed to remove MinIO object of deleted media. It will be retried. fileKey={}", fileKey, e);
            return false;
        }
    }
}
