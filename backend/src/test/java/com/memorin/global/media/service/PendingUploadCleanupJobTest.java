package com.memorin.global.media.service;

import com.memorin.domain.pending_upload.entity.PendingUpload;
import com.memorin.domain.pending_upload.repository.PendingUploadRepository;
import com.memorin.global.media.MinioProperties;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 이슈 #70 P2 회귀 테스트: 커밋 없이 만료된 pending 예약은 MinIO 오브젝트와 DB 행을 함께 정리해야 한다
// (그렇지 않으면 파일만 스토리지에 남고 quota/DB엔 흔적이 없는 고아 오브젝트 + 회계 드리프트가 생긴다).
@ExtendWith(MockitoExtension.class)
class PendingUploadCleanupJobTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PendingUploadRepository pendingUploadRepository;

    @Mock
    private MinioClient minioClient;

    private final MinioProperties properties = new MinioProperties(
            "http://minio:9000", "http://localhost:9000", "us-east-1",
            "key", "secret", "bucket", 600, 300, 1000L, List.of("image/png")
    );

    private PendingUploadCleanupJob job() {
        return new PendingUploadCleanupJob(pendingUploadRepository, minioClient, properties, FIXED_CLOCK);
    }

    @Test
    void 만료된_예약의_오브젝트를_지우고_DB_행도_지운다() throws Exception {
        PendingUpload expired = PendingUpload.of(
                UUID.randomUUID(), "uploads/orphan.png", 500L, LocalDateTime.now(FIXED_CLOCK).minusMinutes(1));
        given(pendingUploadRepository.findByExpiresAtBefore(any())).willReturn(List.of(expired));

        job().cleanupExpiredReservations();

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(pendingUploadRepository).delete(expired);
    }

    @Test
    void MinIO_삭제가_실패하면_DB_행은_남겨서_다음_주기에_재시도한다() throws Exception {
        PendingUpload expired = PendingUpload.of(
                UUID.randomUUID(), "uploads/orphan.png", 500L, LocalDateTime.now(FIXED_CLOCK).minusMinutes(1));
        given(pendingUploadRepository.findByExpiresAtBefore(any())).willReturn(List.of(expired));
        willThrow(new RuntimeException("minio down")).given(minioClient).removeObject(any(RemoveObjectArgs.class));

        job().cleanupExpiredReservations();

        verify(pendingUploadRepository, never()).delete(any());
    }
}
