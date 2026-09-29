package com.memorin.global.media.service;

import com.memorin.global.media.MinioProperties;
import com.memorin.global.media.dto.request.PresignedUploadRequest;
import com.memorin.global.media.dto.response.PresignedUploadResponse;
import com.memorin.global.media.exception.UploadSizeExceededException;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 이슈 #296 회귀 테스트: presigned PUT URL이 Content-Length를 서명 헤더에 넣어
// 선언한 크기와 다른 본문을 스토리지가 거절하게 하는지 확인한다.
// URL 서명은 실제 MinIO SDK로 만든다. region을 지정하면 SDK가 네트워크 호출 없이 서명만 하므로
// 스토리지 없이도 X-Amz-SignedHeaders에 무엇이 들어가는지 검증할 수 있다.
class PresignedUploadServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC);
    private static final long MAX_UPLOAD_SIZE_BYTES = 52_428_800L;

    private final MinioClient minioClient = mock(MinioClient.class);
    private final StorageQuotaService storageQuotaService = mock(StorageQuotaService.class);

    private final MinioProperties properties = new MinioProperties(
            "http://minio:9000", "http://localhost:9000", "us-east-1",
            "test-access-key", "test-secret-key", "memorin-media",
            600, 300, MAX_UPLOAD_SIZE_BYTES, List.of("image/jpeg", "video/mp4")
    );

    private final MinioClient presignedUrlMinioClient = MinioClient.builder()
            .endpoint(properties.publicEndpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .region(properties.region())
            .build();

    private PresignedUploadService service() throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class))).willReturn(true);
        return new PresignedUploadService(
                minioClient, presignedUrlMinioClient, properties, storageQuotaService, FIXED_CLOCK
        );
    }

    @Test
    void 업로드_URL은_Content_Length와_Content_Type을_서명한다() throws Exception {
        PresignedUploadRequest request = new PresignedUploadRequest("photo.jpg", "image/jpeg", 1_048_576L);

        PresignedUploadResponse response = service().createUploadUrl(UUID.randomUUID(), request);

        assertThat(response.uploadUrl())
                .contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost");
    }

    @Test
    void requiredHeaders에_선언한_크기를_Content_Length로_내려준다() throws Exception {
        PresignedUploadRequest request = new PresignedUploadRequest("clip.mp4", "video/mp4", 3_210_987L);

        PresignedUploadResponse response = service().createUploadUrl(UUID.randomUUID(), request);

        assertThat(response.requiredHeaders())
                .containsOnly(
                        entry("Content-Type", "video/mp4"),
                        entry("Content-Length", "3210987")
                );
        assertThat(response.method()).isEqualTo("PUT");
        assertThat(response.maxUploadSizeBytes()).isEqualTo(MAX_UPLOAD_SIZE_BYTES);
    }

    @Test
    void 예약은_선언한_크기로_한다() throws Exception {
        UUID userId = UUID.randomUUID();
        PresignedUploadRequest request = new PresignedUploadRequest("photo.jpg", "image/jpeg", 2_048L);

        PresignedUploadResponse response = service().createUploadUrl(userId, request);

        verify(storageQuotaService).reserveUpload(eq(userId), eq(response.objectKey()), eq(2_048L));
    }

    @Test
    void 선언한_크기가_단일_파일_상한을_넘으면_URL을_발급하지_않는다() throws Exception {
        PresignedUploadService service = service();
        PresignedUploadRequest request =
                new PresignedUploadRequest("big.mp4", "video/mp4", MAX_UPLOAD_SIZE_BYTES + 1);

        assertThatThrownBy(() -> service.createUploadUrl(UUID.randomUUID(), request))
                .isInstanceOf(UploadSizeExceededException.class);
        verify(storageQuotaService, never()).reserveUpload(any(), anyString(), anyLong());
    }
}
