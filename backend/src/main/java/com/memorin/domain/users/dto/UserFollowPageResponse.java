package com.memorin.domain.users.dto;

import java.util.List;
import java.util.UUID;

public record UserFollowPageResponse(

    List<UserFollowResponse> items,

    UUID nextCursor,

    boolean hasNext

) {
}
