package com.memorin.domain.follows.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FollowRequest(

    @NotNull
    UUID followingId
) {
}
