package com.memorin.domain.chat_rooms.dto.response;

import com.memorin.domain.chat_rooms.entity.Chat_type;

import java.util.UUID;

public record ChatRoomResponse(

    UUID roomId,
    Chat_type type,
    String name,
    boolean newlyCreated

) {}
