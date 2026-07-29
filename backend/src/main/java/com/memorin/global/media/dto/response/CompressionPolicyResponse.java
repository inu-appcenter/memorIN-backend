package com.memorin.global.media.dto.response;

public record CompressionPolicyResponse(
        int imageQualityPercent,
        int imageMaxWidthPx,
        int imageMaxHeightPx
) {
}
