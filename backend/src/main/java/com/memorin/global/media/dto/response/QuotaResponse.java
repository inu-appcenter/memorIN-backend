package com.memorin.global.media.dto.response;

public record QuotaResponse(
        long usedBytes,
        long totalQuotaBytes,
        long remainingBytes,
        double usagePercent,
        boolean warning
) {
}
