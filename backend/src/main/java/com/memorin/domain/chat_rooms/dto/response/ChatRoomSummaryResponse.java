package com.memorin.domain.chat_rooms.dto.response;

import com.memorin.domain.chat_room_members.entity.Members_role;
import com.memorin.domain.chat_rooms.entity.Chat_type;

import java.util.UUID;

public record ChatRoomSummaryResponse(

    UUID roomId,
    Chat_type type,
    String name,
    Members_role myRole

) {
}
