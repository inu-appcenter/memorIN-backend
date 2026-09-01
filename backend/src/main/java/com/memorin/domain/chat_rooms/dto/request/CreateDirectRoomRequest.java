package com.memorin.domain.chat_rooms.dto.request;

import java.util.UUID;

public record CreateDirectRoomRequest(

    UUID targetUserId

) {
}
