package com.memorin.domain.web_push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebPushSubscriptionRequest(
    @NotBlank
    @Size(max = 2048) // WebPushEndpointPolicy.MAX_LENGTH, DB 컬럼 길이
    String endpoint,

    @NotBlank
    @Size(max = 256)
    String p256dh,

    @NotBlank
    @Size(max = 256)
    String auth
) {
}
