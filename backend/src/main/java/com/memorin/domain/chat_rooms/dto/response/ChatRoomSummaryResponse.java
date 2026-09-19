package com.memorin.domain.chat_rooms.dto.response;

import com.memorin.domain.chat_room_members.entity.Members_role;
import com.memorin.domain.chat_room_members.repository.projection.RoomActivityProjection;
import com.memorin.domain.chat_rooms.entity.Chat_type;
import com.memorin.domain.messages.entity.MessageType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatRoomSummaryResponse(

    UUID roomId,
    Chat_type type,
    String name,
    Members_role myRole,
    long unreadCount,
    LastMessagePreview lastMessage

) {

    public static ChatRoomSummaryResponse of(UUID roomId, Chat_type type, String name, Members_role myRole,
                                              RoomActivityProjection activity) {
        return new ChatRoomSummaryResponse(
            roomId, type, name, myRole,
            activity.getUnreadCount(),
            LastMessagePreview.from(activity)
        );
    }

    // 방에 메시지가 한 번도 없으면 lastMessageId가 null이다(LEFT JOIN LATERAL).
    public record LastMessagePreview(String preview, LocalDateTime sentAt) {

        private static LastMessagePreview from(RoomActivityProjection activity) {
            if (activity.getLastMessageId() == null) {
                return null;
            }
            return new LastMessagePreview(previewOf(activity), activity.getLastMessageSentAt());
        }

        // type 컬럼만으로 분기한다 — IMAGE·POST_SHARE는 content(jsonb)를 파싱할 필요가 없다.
        private static String previewOf(RoomActivityProjection activity) {
            return switch (MessageType.valueOf(activity.getLastMessageType())) {
                case TEXT -> activity.getLastMessageText();
                case IMAGE -> "사진";
                case POST_SHARE -> "게시물을 공유했습니다";
            };
        }
    }
}
