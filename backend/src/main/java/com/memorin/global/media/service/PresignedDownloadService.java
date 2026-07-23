package com.memorin.global.media.service;

import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.global.media.MinioProperties;
import com.memorin.global.media.dto.response.PresignedDownloadResponse;
import com.memorin.global.media.exception.MediaAccessDeniedException;
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

    // 단건 API용 - id만 아는 경우. 요청자가 게시물 소유자이거나 게시물이 PUBLIC일 때만 발급한다.
    // (누구나 인증만 되면 임의 postMediaId로 남의 비공개 미디어 URL을 뽑아낼 수 있던 IDOR 방지)
    public PresignedDownloadResponse createDownloadUrl(UUID postMediaId, UUID requesterId) {
        PostMedia postMedia = postMediaRepository.findById(postMediaId)
                .orElseThrow(() -> new PostMediaNotFoundException(postMediaId));
        if (!postMedia.getPost().isVisibleTo(requesterId)) {
            throw new MediaAccessDeniedException(postMediaId);
        }
        return createDownloadUrl(postMedia);
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

    // 목록용 - 이미 엔티티를 손에 든 경우 DB 재조회 없음.
    public PresignedDownloadResponse createDownloadUrl(PostMedia postMedia) {
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
