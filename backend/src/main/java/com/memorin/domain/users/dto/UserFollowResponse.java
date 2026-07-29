package com.memorin.domain.users.dto;

import com.memorin.domain.users.entity.User;

import java.util.UUID;

public record UserFollowResponse(

    UUID id,
    String username,
    String displayName,
    String profileImageKey

) {
    public static UserFollowResponse from(User user) {

        return new UserFollowResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getProfileImageKey()
        );
    }
}
