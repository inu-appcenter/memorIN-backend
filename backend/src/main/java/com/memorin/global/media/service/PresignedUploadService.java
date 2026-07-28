package com.memorin.global.media.service;

import com.memorin.global.media.MinioProperties;
import com.memorin.global.media.dto.request.PresignedUploadRequest;
import com.memorin.global.media.dto.response.PresignedUploadResponse;
import com.memorin.global.media.exception.MediaStorageException;
import com.memorin.global.media.exception.UnsupportedContentTypeException;
import com.memorin.global.media.exception.UploadSizeExceededException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PresignedUploadService {

    private static final Logger log = LoggerFactory.getLogger(PresignedUploadService.class);

    private final MinioClient minioClient;
    private final MinioClient presignedUrlMinioClient;
    private final MinioProperties properties;
    private final StorageQuotaService storageQuotaService;
    private final Clock clock;
    private final Set<String> allowedContentTypes;
    private volatile boolean bucketReady = false;

    @Autowired
    public PresignedUploadService(
            @Qualifier("minioClient") MinioClient minioClient,
            @Qualifier("presignedUrlMinioClient") MinioClient presignedUrlMinioClient,
            MinioProperties properties,
            StorageQuotaService storageQuotaService
    ) {
        this(minioClient, presignedUrlMinioClient, properties, storageQuotaService, Clock.systemUTC());
    }

    PresignedUploadService(
            MinioClient minioClient,
            MinioClient presignedUrlMinioClient,
            MinioProperties properties,
            StorageQuotaService storageQuotaService,
            Clock clock
    ) {
        this.minioClient = minioClient;
        this.presignedUrlMinioClient = presignedUrlMinioClient;
        this.properties = properties;
        this.storageQuotaService = storageQuotaService;
        this.clock = clock;
        this.allowedContentTypes = Set.copyOf(properties.allowedContentTypes());
    }

    public PresignedUploadResponse createUploadUrl(UUID userId, PresignedUploadRequest request) {
        validateRequest(request);

        String objectKey = createObjectKey(request.fileName());
        // committed+pending 합산 검증 + pending 예약을 원자적으로 수행한다 (TOCTOU 방지).
        // 실제 업로드 크기는 여기서 검증되지 않는다 - 클라이언트가 선언한 contentLength일 뿐이고,
        // 게시물에 첨부로 커밋될 때 MinIO statObject로 재검증한 실제 크기가 최종 반영된다.
        storageQuotaService.reserveUpload(userId, objectKey, request.contentLength());

        Map<String, String> requiredHeaders = Map.of("Content-Type", request.contentType());

        try {
            ensureBucketReady();

            String uploadUrl = presignedUrlMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(properties.bucketName())
                            .object(objectKey)
                            .expiry(properties.presignedUploadExpirySeconds())
                            .extraHeaders(requiredHeaders)
                            .build()
            );

            return new PresignedUploadResponse(
                    uploadUrl,
                    objectKey,
                    "PUT",
                    requiredHeaders,
                    Instant.now(clock).plusSeconds(properties.presignedUploadExpirySeconds()),
                    properties.maxUploadSizeBytes()
            );
        } catch (Exception e) {
            log.error("Failed to create presigned upload URL. bucket={}, objectKey={}, contentType={}",
                    properties.bucketName(), objectKey, request.contentType(), e);
            throw new MediaStorageException("Presigned upload URL creation failed.", e);
        }
    }

    // 버킷 존재 확인+생성은 프로세스 생애주기 동안 딱 한 번만 실제로 수행한다 (이후는 필드 체크로 즉시 반환).
    // 매 발급 요청마다 했을 때의 문제:
    // (a) 동시에 들어온 최초 요청끼리 경쟁해 makeBucket이 BucketAlreadyOwnedByYou류 오류로 500이 났다.
    // (b) presigned URL 생성 자체는 서명 연산이라 네트워크 호출이 필요 없는데, MinIO(관리 API)가
    //     느리거나 죽어 있으면 매번 이 체크 때문에 발급까지 덩달아 실패했다.
    // 앱 기동 시(@PostConstruct)가 아니라 첫 실제 사용 시점에 하는 이유: MinIO를 쓰지 않는
    // 테스트(@SpringBootTest 전체 컨텍스트 등)까지 기동 시점에 MinIO 연결을 강제하지 않기 위해서다.
    private void ensureBucketReady() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            ensureBucketExists();
            bucketReady = true;
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(properties.bucketName())
                        .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(properties.bucketName())
                            .build()
            );
            log.info("Created MinIO bucket: {}", properties.bucketName());
        }
    }

    private void validateRequest(PresignedUploadRequest request) {
        if (!allowedContentTypes.contains(request.contentType())) {
            throw new UnsupportedContentTypeException(request.contentType());
        }
        if (request.contentLength() > properties.maxUploadSizeBytes()) {
            throw new UploadSizeExceededException(request.contentLength(), properties.maxUploadSizeBytes());
        }
    }

    private String createObjectKey(String fileName) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return "uploads/%d/%02d/%02d/%s/%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                sanitizeFileName(fileName)
        );
    }

    private String sanitizeFileName(String fileName) {
        String normalized = fileName.strip().toLowerCase(Locale.ROOT);
        String sanitized = normalized.replaceAll("[^a-z0-9._-]", "_");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            return "upload";
        }
        return sanitized;
    }
}
