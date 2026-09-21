package com.memorin.global.media.service;

import com.memorin.global.media.MinioProperties;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class UserMediaDeletionService {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public UserMediaDeletionService(@Qualifier("minioClient") MinioClient minioClient,
                                    MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.bucketName())
                .object(objectKey)
                .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete user media object.", e);
        }
    }
}
