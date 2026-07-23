package com.memorin.domain.posts.dto.response;

import java.util.List;

public record PostListResponse(
        List<PostSummaryResponse> items,
        String nextCursor,
        boolean hasNext
) {}
