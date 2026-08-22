package com.memorin.domain.users.dto;

import com.memorin.domain.users.entity.User;

import java.util.UUID;

public record UserProfileResponse(
    UUID userId,
    String username,
    String displayName,
    String profileImage,
    String bio
) {
    public static UserProfileResponse from(User user, String profileImageUrl) {
        return new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            profileImageUrl,
            user.getBio()
        );
    }
}
