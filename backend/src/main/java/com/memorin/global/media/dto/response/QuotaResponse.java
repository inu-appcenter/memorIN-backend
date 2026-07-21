package com.memorin.global.media.dto.response;

public record QuotaResponse(
        long usedBytes,
        long limitBytes,
        long remainingBytes,
        double usagePercentage
) {
}
