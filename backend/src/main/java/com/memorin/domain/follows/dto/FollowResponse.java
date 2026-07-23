package com.memorin.domain.follows.dto;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.entity.Follow_state;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowResponse(

    UUID id,
    UUID followingId,
    Follow_state status,
    LocalDateTime createdAt
) {

    public static FollowResponse from(Follows follow) {

        return new FollowResponse(
                follow.getId(),
                follow.getFollowing().getId(),
                follow.getStatus(),
                follow.getCreatedAt()
        );
    }
}
