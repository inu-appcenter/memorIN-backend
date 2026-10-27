package com.memorin.domain.follows.repository;

import java.util.UUID;

public record FollowRecommendationRow(

    UUID candidateId,
    String displayName,
    String profileImageKey,
    long mutualFriendCount,
    long score

) {
}
