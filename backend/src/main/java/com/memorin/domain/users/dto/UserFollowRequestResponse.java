package com.memorin.domain.users.dto;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.users.entity.User;

import java.util.UUID;

public record UserFollowRequestResponse(
    UUID followId,
    UUID userId,
    String username,
    String displayName,
    String bio
) {

    public static UserFollowRequestResponse from(Follows follows) {
        User user = follows.getFollower();

        return new UserFollowRequestResponse(
            follows.getId(),
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getBio()
        );
    }
}
