package com.memorin.global.config;

import java.util.UUID;

/**
 * STOMP 목적지 규칙.
 *
 * <p>구독을 받을 때(인바운드)와 메시지를 내보낼 때(아웃바운드) 양쪽이 같은 문자열을 파싱한다.
 * 두 곳이 각자 자르면 한쪽만 고쳤을 때 조용히 어긋나므로 한곳에 둔다.
 */
final class ChatRoomDestinations {

    /** {@code MessageController}가 브로드캐스트하는 목적지 접두사. 클라이언트가 구독할 수 있는 유일한 목적지다. */
    static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

    /** 클라이언트가 SEND할 수 있는 유일한 접두사. {@code @MessageMapping} 핸들러로만 간다. */
    static final String APPLICATION_PREFIX = "/app/";

    /**
     * 목적지가 방 토픽이면 roomId를, 아니면 {@code null}을 반환한다.
     *
     * <p>UUID의 <b>정규 표기(소문자 8-4-4-4-12)</b>만 받는다. {@code UUID.fromString}은 {@code 1-1-1-1-1} 같은
     * 표기도 받아들이고, simple broker는 {@code *}·{@code **}·{@code {var}}를 패턴으로 해석한다.
     * 정규 표기와 문자열이 정확히 같아야 하므로 패턴 문자가 끼어들 자리가 없다(#304).
     *
     * <p>형식이 어긋나는 경우도 {@code null}이다. 인바운드는 이를 거절하고,
     * 아웃바운드는 우리가 만든 목적지가 아니므로 판단하지 않는다.
     */
    static UUID parseRoomIdOrNull(String destination) {
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return null;
        }
        String raw = destination.substring(ROOM_TOPIC_PREFIX.length());
        try {
            UUID roomId = UUID.fromString(raw);
            return roomId.toString().equals(raw) ? roomId : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean isApplicationDestination(String destination) {
        return destination != null && destination.startsWith(APPLICATION_PREFIX);
    }

    private ChatRoomDestinations() {
    }
}
