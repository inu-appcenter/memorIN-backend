package com.memorin.global.media;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "storage.quota")
public record StorageQuotaProperties(
        @Min(1) long defaultLimitBytes,
        @Min(1) long maxSingleUploadBytes,
        @Min(1) long pendingTtlSeconds
) {
}
