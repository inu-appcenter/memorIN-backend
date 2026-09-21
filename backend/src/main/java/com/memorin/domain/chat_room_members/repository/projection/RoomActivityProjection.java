package com.memorin.domain.chat_room_members.repository.projection;

import java.time.LocalDateTime;
import java.util.UUID;

// findRoomActivityByUserId 전용 프로젝션. 메시지가 한 번도 없던 방은
// lastMessage* 필드가 전부 null로 채워진다(LEFT JOIN LATERAL).
public interface RoomActivityProjection {

    UUID getRoomId();

    long getUnreadCount();

    UUID getLastMessageId();

    String getLastMessageType();

    String getLastMessageText();

    LocalDateTime getLastMessageSentAt();
}
