package com.memorin.media;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    @Qualifier("minioClient")
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    @Qualifier("presignedUrlMinioClient")
    public MinioClient presignedUrlMinioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
