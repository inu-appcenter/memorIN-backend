package com.memorin.domain.users.dto;

import java.util.UUID;

public record UserSearchResponse(

    UUID id,
    String username,
    String displayName,
    String bio
) {
}
