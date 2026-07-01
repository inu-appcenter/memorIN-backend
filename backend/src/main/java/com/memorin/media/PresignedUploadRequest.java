package com.memorin.media;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignedUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Min(1) Long contentLength
) {
}
