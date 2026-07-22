package com.memorin.domain.posts.dto.Response;

import java.util.List;

public record PostListResponse(
        List<PostSummaryResponse> items,
        String nextCursor,
        boolean hasNext
) {}
