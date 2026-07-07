package com.memorin.global.media;

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
    private final Clock clock;
    private final Set<String> allowedContentTypes;

    @Autowired
    public PresignedUploadService(
            @Qualifier("minioClient") MinioClient minioClient,
            @Qualifier("presignedUrlMinioClient") MinioClient presignedUrlMinioClient,
            MinioProperties properties
    ) {
        this(minioClient, presignedUrlMinioClient, properties, Clock.systemUTC());
    }

    PresignedUploadService(
            MinioClient minioClient,
            MinioClient presignedUrlMinioClient,
            MinioProperties properties,
            Clock clock
    ) {
        this.minioClient = minioClient;
        this.presignedUrlMinioClient = presignedUrlMinioClient;
        this.properties = properties;
        this.clock = clock;
        this.allowedContentTypes = Set.copyOf(properties.allowedContentTypes());
    }

    public PresignedUploadResponse createUploadUrl(PresignedUploadRequest request) {
        validateRequest(request);

        String objectKey = createObjectKey(request.fileName());
        Map<String, String> requiredHeaders = Map.of("Content-Type", request.contentType());

        try {
            ensureBucketExists();

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
            throw new IllegalArgumentException("Unsupported content type: " + request.contentType());
        }
        if (request.contentLength() > properties.maxUploadSizeBytes()) {
            throw new IllegalArgumentException("File size exceeds max upload size.");
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
