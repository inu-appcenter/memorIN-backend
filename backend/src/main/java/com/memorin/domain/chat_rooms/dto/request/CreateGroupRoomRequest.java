package com.memorin.domain.chat_rooms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateGroupRoomRequest(

    @NotBlank @Size(max = 100) String name,
    @NotEmpty List<UUID> memberIds

) {
}
