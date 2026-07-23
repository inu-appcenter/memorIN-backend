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
// MinIO statObject로 확인한 실제 업로드 크기만 사용한다.
// (presigned PUT은 서명이 body 크기를 강제하지 않아 클라이언트가 발급 시 선언한 값보다
//  훨씬 큰 파일을 그대로 업로드할 수 있다 - 이 지점이 그걸 막는 유일한 검증 지점이다.)
@Service
public class MediaUploadCommitService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final PendingUploadRepository pendingUploadRepository;
    private final Clock clock;

    @Autowired
    public MediaUploadCommitService(
            @Qualifier("minioClient") MinioClient minioClient,
            MinioProperties minioProperties,
            PendingUploadRepository pendingUploadRepository
    ) {
        this(minioClient, minioProperties, pendingUploadRepository, Clock.systemUTC());
    }

    MediaUploadCommitService(

            MinioClient minioClient,
            MinioProperties minioProperties,
            PendingUploadRepository pendingUploadRepository,
            Clock clock
    ) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.pendingUploadRepository = pendingUploadRepository;
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
