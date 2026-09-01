package com.memorin.domain.chat_rooms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameRoomRequest(

    @NotBlank @Size(max = 100) String name

) {
}
