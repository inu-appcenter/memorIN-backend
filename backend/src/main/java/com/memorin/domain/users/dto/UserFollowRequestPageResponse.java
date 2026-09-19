package com.memorin.domain.users.dto;

import java.util.List;
import java.util.UUID;

public record UserFollowRequestPageResponse(
    List<UserFollowRequestResponse> items,
    UUID nextCursor,
    boolean hasNext
) {
}
