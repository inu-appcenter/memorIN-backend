package com.memorin.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String publicEndpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucketName,
        @Min(60) @Max(604800) int presignedUploadExpirySeconds,
        @Min(1) long maxUploadSizeBytes,
        @NotEmpty List<String> allowedContentTypes
) {
}
