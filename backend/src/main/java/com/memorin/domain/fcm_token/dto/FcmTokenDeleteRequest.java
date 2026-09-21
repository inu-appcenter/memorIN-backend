package com.memorin.domain.fcm_token.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenDeleteRequest(@NotBlank String token) {
}
