package com.memorin.domain.follows.dto;

import java.util.List;

public record FollowRecommendationListResponse(
    List<FollowRecommendationResponse> items,
    String nextCursor,
    boolean hasNext
) {}
