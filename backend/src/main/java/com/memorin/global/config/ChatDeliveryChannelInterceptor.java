package com.memorin.global.config;

import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import com.memorin.global.exception.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * 방 메시지를 내보내기 직전에 "지금도 이 방의 멤버인가"를 다시 확인한다.
 *
 * <p><b>왜 SUBSCRIBE 검사만으로 부족한가</b> — {@link StompAuthChannelInterceptor}는 구독을
 * <i>요청하는 순간</i>에만 검사한다. 이미 구독을 맺은 뒤에 강퇴당하거나 방을 나가면
 * 그 구독은 그대로 살아 있다. 브로커(InMemory)는 구독 레지스트리만 보고 뿌리므로
 * DB에서 멤버가 빠져도 메시지는 계속 간다.
 *
 * <pre>
 * 1. A가 방에 참여 → /topic/rooms/{id} 구독 (정상 통과)
 * 2. 방장이 A를 강퇴 → chat_room_members.left_at 기록
 * 3. A의 세션은 그대로 → 이후 메시지가 계속 A에게 전달됨   ← 여기를 막는다
 * </pre>
 *
 * <p>발신(SEND)은 이미 막혀 있었다. {@code MessageService}가 매 요청 활성 멤버를 확인한다.
 * 뚫려 있던 것은 수신뿐이고, "강퇴당한 직후부터 그 사람이 탭을 닫거나 새로고침할 때까지"가
 * 그 창이었다. 짧지 않을 수 있다. (#210)
 *
 * <p><b>왜 이 방식인가</b> — STOMP에는 서버가 보내는 UNSUBSCRIBE 프레임이 없다. 남는 선택지는
 * 세션을 끊거나, 배달 시점에 거르는 것이다. 세션을 끊으면 그 사용자가 보고 있던 다른 방까지
 * 함께 끊긴다. 배달 시점 필터는 FE 계약(구독 목적지)을 바꾸지 않으면서 창을 완전히 닫는다.
 * 매 배달마다 DB를 치지 않도록 판정은 {@link ChatRoomMembershipGate}가 캐시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDeliveryChannelInterceptor implements ChannelInterceptor {

    private final ChatRoomMembershipGate membershipGate;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.MESSAGE.equals(accessor.getCommand())) {
            // 브로커가 구독자에게 보내는 프레임만 본다. CONNECTED·RECEIPT·ERROR 등은 관심 밖이다.
            return message;
        }

        UUID roomId = ChatRoomDestinations.parseRoomIdOrNull(accessor.getDestination());
        if (roomId == null) {
            // 방 토픽이 아니다. 우리가 만든 목적지가 아니므로 판단하지 않는다.
            return message;
        }

        UUID userId = userIdOf(accessor.getUser());
        if (userId == null) {
            // 인증된 세션만 방 토픽을 구독할 수 있으므로 정상 경로에서는 올 수 없다.
            // 방어적으로 막는다 — 여기서 통과시키면 인증 없는 세션에 대화가 흘러간다.
            log.warn("Principal 없는 세션으로 방 메시지가 나가려 했다. destination={}", accessor.getDestination());
            return null;
        }

        if (membershipGate.isActiveMember(userId, roomId)) {
            return message;
        }

        // null을 반환하면 이 세션으로는 나가지 않는다. 다른 구독자에게는 영향이 없다.
        log.debug("활성 멤버가 아니라 배달을 취소한다. userId={}, roomId={}", userId, roomId);
        return null;
    }

    private UUID userIdOf(Principal user) {
        if (user instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }
}
