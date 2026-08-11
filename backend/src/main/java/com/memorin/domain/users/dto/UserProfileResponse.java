package com.memorin.domain.users.dto;

import com.memorin.domain.users.entity.User;

public record UserProfileResponse(
    String userId,
    String username,
    String displayName,
    String profileImage,
    String bio
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
            user.getId().toString(),
            user.getUsername(),
            user.getDisplayName(),
            user.getProfileImageKey(),
            user.getBio()
        );
    }
}
