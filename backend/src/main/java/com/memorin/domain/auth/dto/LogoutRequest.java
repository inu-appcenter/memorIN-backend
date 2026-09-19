package com.memorin.domain.auth.dto;

public record LogoutRequest(
    String fcmToken,
    String webPushEndpoint
) {
}
