package com.memorin.domain.follows.dto;

import java.util.UUID;

public record FollowRecommendationResponse(
    UUID userId,
    String displayName,
    String profileImageUrl,
    long mutualFriendCount
) {}
