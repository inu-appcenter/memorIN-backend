package com.memorin.domain.web_push.dto;

import jakarta.validation.constraints.NotBlank;

public record WebPushSubscriptionDeleteRequest(@NotBlank String endpoint) {
}
