package com.memorin.global.config;

import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 방 메시지를 내보내기 직전에 "이 세션의 사용자가 지금도 이 방의 멤버인가"를 다시 확인한다.
 *
 * <p><b>왜 SUBSCRIBE 검사만으로 부족한가</b> — {@link StompAuthChannelInterceptor}는 구독을
 * <i>요청하는 순간</i>에만 검사한다. 이미 구독을 맺은 뒤에 강퇴당하거나 방을 나가면
 * 그 구독은 그대로 살아 있다. 브로커(InMemory)는 구독 레지스트리만 보고 뿌리므로
 * DB에서 멤버가 빠져도 메시지는 계속 간다. (#210)
 *
 * <p><b>헤더를 읽는 방법(#304)</b> — simple broker가 구독자에게 보내는 메시지의 헤더는
 * {@link SimpMessageHeaderAccessor}(메시지 타입 {@code MESSAGE})다. {@code StompHeaderAccessor}가 아니다.
 * 예전 코드는 {@code StompHeaderAccessor}로 꺼내 항상 null을 받았고, 그래서 모든 메시지를 그대로
 * 통과시켰다. 또 이 메시지의 {@code simpUser}는 받는 사람이 아니다(발신 메시지의 헤더가 복사되므로
 * 오히려 보낸 사람일 수 있다). 받는 사람은 {@code simpSessionId}로만 알 수 있으므로
 * 세션 ID → 사용자 매핑({@link WebSocketSessionRegistry})으로 찾는다.
 *
 * <p>매 배달마다 DB를 치지 않도록 판정은 {@link ChatRoomMembershipGate}가 캐시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDeliveryChannelInterceptor implements ChannelInterceptor {

    private final ChatRoomMembershipGate membershipGate;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // 헤더는 정적 메서드로 읽는다. 접근자가 잠겨 있거나 다른 하위 타입이어도 같은 값을 얻는다.
        MessageHeaders headers = message.getHeaders();
        if (SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.MESSAGE) {
            // 구독자에게 가는 메시지만 본다. CONNECT_ACK·HEARTBEAT·DISCONNECT_ACK 등은 관심 밖이다.
            return message;
        }

        String destination = SimpMessageHeaderAccessor.getDestination(headers);
        UUID roomId = ChatRoomDestinations.parseRoomIdOrNull(destination);
        if (roomId == null) {
            // 방 토픽이 아니다. 구독 자체가 방 토픽만 허용되므로 정상 경로에서는 오지 않는다.
            return message;
        }

        String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
        UUID userId = sessionId == null ? null : sessionRegistry.userIdOf(sessionId);
        if (userId == null) {
            // 인증된 세션만 방 토픽을 구독할 수 있으므로 정상 경로에서는 올 수 없다.
            // 모르는 세션이면 막는다 — 통과시키면 인증 없는 세션에 대화가 흘러간다.
            log.warn("사용자를 알 수 없는 세션으로 방 메시지가 나가려 했다. sessionId={}, destination={}",
                sessionId, destination);
            return null;
        }

        if (membershipGate.isActiveMember(userId, roomId)) {
            return message;
        }

        // null을 반환하면 이 세션으로는 나가지 않는다. 다른 구독자에게는 영향이 없다.
        log.debug("활성 멤버가 아니라 배달을 취소한다. userId={}, roomId={}", userId, roomId);
        return null;
    }
}
