package com.memorin.global.media.service;

import com.memorin.domain.pending_upload.entity.PendingUpload;
import com.memorin.domain.pending_upload.repository.PendingUploadRepository;
import com.memorin.global.media.MinioProperties;
import io.minio.RemoveObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// 커밋(게시물 첨부) 없이 pendingTtlSeconds가 지난 예약을 정리한다.
// 그대로 두면 파일만 MinIO에 올라간 채 quota/DB엔 흔적이 없는 "고아 오브젝트 + 회계 드리프트"가
// 영구히 남는다 (docs/storage-quota-design.md 확인 필요 항목: PENDING 만료 데이터 정리 정책).
@Component
public class PendingUploadCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PendingUploadCleanupJob.class);

    private final PendingUploadRepository pendingUploadRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final Clock clock;

    @Autowired
    public PendingUploadCleanupJob(
            PendingUploadRepository pendingUploadRepository,
            @Qualifier("minioClient") MinioClient minioClient,
            MinioProperties minioProperties
    ) {
        this(pendingUploadRepository, minioClient, minioProperties, Clock.systemUTC());
    }

    PendingUploadCleanupJob(
            PendingUploadRepository pendingUploadRepository,
            MinioClient minioClient,
            MinioProperties minioProperties,
            Clock clock
    ) {
        this.pendingUploadRepository = pendingUploadRepository;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${storage.quota.pending-cleanup-interval-ms:300000}")
    @Transactional
    public void cleanupExpiredReservations() {
        List<PendingUpload> expired = pendingUploadRepository.findByExpiresAtBefore(LocalDateTime.now(clock));
        for (PendingUpload reservation : expired) {
            if (removeOrphanedObject(reservation.getObjectKey())) {
                pendingUploadRepository.delete(reservation);
            }
            // MinIO 삭제가 실패하면 DB 행은 남겨서 다음 주기에 재시도한다.
        }
    }

    private boolean removeOrphanedObject(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.bucketName())
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.warn("Failed to remove orphaned MinIO object for expired pending upload. objectKey={}", objectKey, e);
            return false;
        }
    }
}
