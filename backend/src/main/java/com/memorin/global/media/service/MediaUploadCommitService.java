package com.memorin.global.media.service;

import com.memorin.domain.pending_upload.entity.PendingUpload;
import com.memorin.domain.pending_upload.repository.PendingUploadRepository;
import com.memorin.global.media.MinioProperties;
import com.memorin.global.media.exception.UploadReservationInvalidException;
import com.memorin.global.media.exception.UploadSizeExceededException;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

// presigned-upload-url 발급 시 만든 pending 예약을, 실제로 게시물에 첨부되는 시점(커밋)에
// 검증하고 확정한다. 클라이언트가 선언한 contentLength는 여기서 전혀 신뢰하지 않고,
// MinIO statObject로 확인한 실제 업로드 크기만 사용해 단일 파일 상한과 유저 전체 quota를
// 다시 검증한다.
// (presigned PUT은 Content-Length를 서명하므로 선언한 크기와 다른 본문은 스토리지가 거절한다(#296).
//  그래도 스토리지 쪽 검증에만 기대지 않도록, 실제 크기를 확정하는 최종 검증 지점은 여기로 유지한다.)
@Service
public class MediaUploadCommitService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final PendingUploadRepository pendingUploadRepository;
    private final StorageQuotaService storageQuotaService;
    private final Clock clock;

    @Autowired
    public MediaUploadCommitService(
            @Qualifier("minioClient") MinioClient minioClient,
            MinioProperties minioProperties,
            PendingUploadRepository pendingUploadRepository,
            StorageQuotaService storageQuotaService
    ) {
        this(minioClient, minioProperties, pendingUploadRepository, storageQuotaService, Clock.systemUTC());
    }

    MediaUploadCommitService(

            MinioClient minioClient,
            MinioProperties minioProperties,
            PendingUploadRepository pendingUploadRepository,
            StorageQuotaService storageQuotaService,
            Clock clock
    ) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.pendingUploadRepository = pendingUploadRepository;
        this.storageQuotaService = storageQuotaService;
        this.clock = clock;
    }

    // 반환값은 statObject로 확인한 실제 바이트 수. 호출자는 이 값을 PostMedia.fileSizeBytes에 써야 한다.
    @Transactional
    public long commitUpload(UUID requesterId, String objectKey) {
        PendingUpload reservation = pendingUploadRepository.findByObjectKey(objectKey)
                .orElseThrow(() -> new UploadReservationInvalidException(objectKey));

        if (!reservation.isOwnedBy(requesterId) || reservation.isExpired(LocalDateTime.now(clock))) {
            throw new UploadReservationInvalidException(objectKey);
        }

        long actualBytes = statObjectSize(objectKey);
        if (actualBytes > minioProperties.maxUploadSizeBytes()) {
            throw new UploadSizeExceededException(actualBytes, minioProperties.maxUploadSizeBytes());
        }
        storageQuotaService.assertCommitWithinLimit(requesterId, reservation.getReservedBytes(), actualBytes);

        pendingUploadRepository.delete(reservation);
        return actualBytes;
    }

    private long statObjectSize(String objectKey) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.bucketName())
                            .object(objectKey)
                            .build()
            ).size();
        } catch (Exception e) {
            // 예약은 있지만 실제로 MinIO에 업로드가 안 된 경우 (presigned URL만 받고 PUT을 안 했거나 실패).
            throw new UploadReservationInvalidException(objectKey);
        }
    }
}
