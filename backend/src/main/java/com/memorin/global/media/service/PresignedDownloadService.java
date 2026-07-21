package com.memorin.global.media.service;

import com.memorin.domain.post_media.Entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.global.media.MinioProperties;
import com.memorin.global.media.dto.response.PresignedDownloadResponse;
import com.memorin.global.media.exception.MediaStorageException;
import com.memorin.global.media.exception.PostMediaNotFoundException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PresignedDownloadService {

    private static final Logger log = LoggerFactory.getLogger(PresignedDownloadService.class);

    private final MinioClient presignedUrlMinioClient;
    private final MinioProperties properties;
    private final PostMediaRepository postMediaRepository;
    private final Clock clock;

    @Autowired
    public PresignedDownloadService(
            @Qualifier("presignedUrlMinioClient") MinioClient presignedUrlMinioClient,
            MinioProperties properties,
            PostMediaRepository postMediaRepository
    ) {
        this(presignedUrlMinioClient, properties, postMediaRepository, Clock.systemUTC());
    }

    PresignedDownloadService(
            MinioClient presignedUrlMinioClient,
            MinioProperties properties,
            PostMediaRepository postMediaRepository,
            Clock clock
    ) {
        this.presignedUrlMinioClient = presignedUrlMinioClient;
        this.properties = properties;
        this.postMediaRepository = postMediaRepository;
        this.clock = clock;
    }

    public PresignedDownloadResponse createDownloadUrl(UUID postMediaId) {
        PostMedia postMedia = postMediaRepository.findById(postMediaId)
                .orElseThrow(() -> new PostMediaNotFoundException(postMediaId));

        String objectKey = postMedia.getFileKey();

        try {
            String downloadUrl = presignedUrlMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.bucketName())
                            .object(objectKey)
                            .expiry(properties.presignedDownloadExpirySeconds())
                            .build()
            );

            return new PresignedDownloadResponse(
                    downloadUrl,
                    objectKey,
                    Instant.now(clock).plusSeconds(properties.presignedDownloadExpirySeconds())
            );
        } catch (Exception e) {
            log.error("Failed to create presigned download URL. bucket={}, objectKey={}",
                    properties.bucketName(), objectKey, e);
            throw new MediaStorageException("Presigned download URL creation failed.", e);
        }
    }
}
