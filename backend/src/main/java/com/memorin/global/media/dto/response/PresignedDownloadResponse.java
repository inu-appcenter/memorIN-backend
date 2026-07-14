package com.memorin.global.media.dto.response;

import java.time.Instant;

public record PresignedDownloadResponse(
        String downloadUrl,
        String objectKey,
        Instant expiresAt
) {
}
