package com.memorin.domain.chat_rooms.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record InviteMembersRequest(

    @NotEmpty List<UUID> memberIds

) {
}
