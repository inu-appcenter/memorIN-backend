package com.memorin.domain.users.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MyPageResponseDto(
    UUID id,
    String email,
    String username,
    String displayName,
    String bio,
    String profileImageUrl,
    LocalDateTime createdAt
) {
}
