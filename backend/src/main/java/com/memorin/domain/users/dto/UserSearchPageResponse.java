package com.memorin.domain.users.dto;

import java.util.List;
import java.util.UUID;

public record UserSearchPageResponse(

    List<UserSearchResponse> items,
    UUID nextCursor,
    boolean hasNext
) {
}
