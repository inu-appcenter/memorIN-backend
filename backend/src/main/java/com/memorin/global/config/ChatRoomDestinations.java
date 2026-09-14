package com.memorin.global.config;

import java.util.UUID;

/**
 * 방 단위 브로드캐스트 목적지 규칙.
 *
 * <p>구독을 받을 때(인바운드)와 메시지를 내보낼 때(아웃바운드) 양쪽이 같은 문자열을 파싱한다.
 * 두 곳이 각자 자르면 한쪽만 고쳤을 때 조용히 어긋나므로 한곳에 둔다.
 */
final class ChatRoomDestinations {

    /** {@code MessageController}가 브로드캐스트하는 목적지 접두사. */
    static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

    /**
     * 목적지가 방 토픽이면 roomId를, 아니면 {@code null}을 반환한다.
     *
     * <p>형식이 어긋나는 경우도 {@code null}이다. 호출부가 "관심 없는 목적지"와
     * "잘못된 목적지"를 각자 판단하게 둔다 — 인바운드는 거절해야 하고,
     * 아웃바운드는 우리가 만들지 않은 목적지이므로 그냥 통과시켜야 한다.
     */
    static UUID parseRoomIdOrNull(String destination) {
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(destination.substring(ROOM_TOPIC_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ChatRoomDestinations() {
    }
}
