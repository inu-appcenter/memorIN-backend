package com.memorin.global.media.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// quota 검증용 userId는 요청 바디가 아니라 JWT(SecurityContext)에서 얻는다.
public record PresignedUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Min(1) Long contentLength
) {
}
