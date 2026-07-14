package com.memorin.global.media.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PresignedUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Min(1) Long contentLength,
        // TODO: JWT 인증 필터 도입 후 SecurityContext에서 꺼내도록 교체하고 이 필드는 제거할 것.
        // 현재는 인증 미구현 상태라 quota 검증용 userId를 요청 바디로 임시 전달받음.
        @NotNull UUID userId
) {
}
