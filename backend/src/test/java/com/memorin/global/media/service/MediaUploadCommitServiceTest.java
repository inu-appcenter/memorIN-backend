package com.memorin.global.media.service;

import com.memorin.domain.pending_upload.entity.PendingUpload;
import com.memorin.domain.pending_upload.repository.PendingUploadRepository;
import com.memorin.global.media.MinioProperties;
import com.memorin.global.media.exception.UploadReservationInvalidException;
import com.memorin.global.media.exception.UploadSizeExceededException;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 이슈 #70 S2 회귀 테스트: 게시물 첨부로 커밋되는 시점에 클라이언트 선언 크기가 아니라
// MinIO statObject로 확인한 실제 크기를 검증/반영하는지 확인한다.
@ExtendWith(MockitoExtension.class)
class MediaUploadCommitServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private MinioClient minioClient;

    @Mock
    private PendingUploadRepository pendingUploadRepository;

    private final MinioProperties properties = new MinioProperties(
            "http://minio:9000", "http://localhost:9000", "us-east-1",
            "key", "secret", "bucket", 600, 300, 1000L, List.of("image/png")
    );

    private MediaUploadCommitService service() {
        return new MediaUploadCommitService(minioClient, properties, pendingUploadRepository, FIXED_CLOCK);
    }

    private PendingUpload reservation(UUID userId, String objectKey, LocalDateTime expiresAt) {
        return PendingUpload.of(userId, objectKey, 500L, expiresAt);
    }

    @Test
    void 예약이_없으면_예외를_던진다() {
        given(pendingUploadRepository.findByObjectKey("missing-key")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().commitUpload(UUID.randomUUID(), "missing-key"))
                .isInstanceOf(UploadReservationInvalidException.class);
    }

    @Test
    void 다른_사용자의_예약이면_예외를_던진다() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        given(pendingUploadRepository.findByObjectKey("key"))
                .willReturn(Optional.of(reservation(owner, "key", LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))));

        assertThatThrownBy(() -> service().commitUpload(stranger, "key"))
                .isInstanceOf(UploadReservationInvalidException.class);
    }

    @Test
    void 만료된_예약이면_예외를_던진다() {
        UUID owner = UUID.randomUUID();
        given(pendingUploadRepository.findByObjectKey("key"))
                .willReturn(Optional.of(reservation(owner, "key", LocalDateTime.now(FIXED_CLOCK).minusSeconds(1))));

        assertThatThrownBy(() -> service().commitUpload(owner, "key"))
                .isInstanceOf(UploadReservationInvalidException.class);
    }

    @Test
    void 실제_업로드_크기가_상한을_넘으면_예외를_던진다() throws Exception {
        UUID owner = UUID.randomUUID();
        given(pendingUploadRepository.findByObjectKey("key"))
                .willReturn(Optional.of(reservation(owner, "key", LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))));
        StatObjectResponse stat = mock(StatObjectResponse.class);
        given(stat.size()).willReturn(5_000L); // 상한(1000L)보다 훨씬 큼 - 클라이언트가 contentLength=1로 속이고 실제로 5GB급을 올린 상황을 흉내
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(stat);

        assertThatThrownBy(() -> service().commitUpload(owner, "key"))
                .isInstanceOf(UploadSizeExceededException.class);
    }

    @Test
    void 정상_커밋시_실제_크기를_반환하고_예약을_지운다() throws Exception {
        UUID owner = UUID.randomUUID();
        PendingUpload reservation = reservation(owner, "key", LocalDateTime.now(FIXED_CLOCK).plusMinutes(10));
        given(pendingUploadRepository.findByObjectKey("key")).willReturn(Optional.of(reservation));
        StatObjectResponse stat = mock(StatObjectResponse.class);
        given(stat.size()).willReturn(777L);
        given(minioClient.statObject(any(StatObjectArgs.class))).willReturn(stat);

        long actualBytes = service().commitUpload(owner, "key");

        assertThat(actualBytes).isEqualTo(777L);
        verify(pendingUploadRepository).delete(reservation);
    }

    @Test
    void statObject_조회가_실패하면_예약_무효로_처리한다() throws Exception {
        UUID owner = UUID.randomUUID();
        given(pendingUploadRepository.findByObjectKey("key"))
                .willReturn(Optional.of(reservation(owner, "key", LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))));
        given(minioClient.statObject(any(StatObjectArgs.class))).willThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> service().commitUpload(owner, "key"))
                .isInstanceOf(UploadReservationInvalidException.class);
    }
}
