package com.memorin.global.media.dto.response;

import java.time.Instant;
import java.util.Map;

public record PresignedUploadResponse(
        String uploadUrl,
        String objectKey,
        String method,
        Map<String, String> requiredHeaders,
        Instant expiresAt,
        long maxUploadSizeBytes
) {
}
