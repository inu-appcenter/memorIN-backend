package com.memorin.global.config;

import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * 클라이언트가 보내는 모든 STOMP 프레임의 인증·인가를 처리한다.
 *
 * <p>HTTP 필터({@code JwtAuthenticationFilter})로는 STOMP를 지킬 수 없다. 필터는 핸드셰이크 요청
 * 한 번만 지나가고, 그 뒤 같은 TCP 연결로 흐르는 STOMP 프레임은 서블릿 필터 체인을 타지 않는다.
 * 그래서 인증 지점을 연결 프레임으로 옮긴다. (docs/sprint4-architecture-review.md §2)
 *
 * <p>규칙(#304)
 * <ul>
 *   <li>{@code CONNECT}·{@code STOMP} — 둘 다 연결 프레임이다. 토큰이 없거나 틀리면 거절한다.
 *       예전에는 {@code CONNECT}만 검사해서 {@code STOMP} 프레임으로 토큰 없이 붙을 수 있었다.</li>
 *   <li>{@code SUBSCRIBE} — {@code /topic/rooms/{uuid}}만, 그 방의 활성 멤버만. 그 밖의 목적지와
 *       패턴 목적지({@code /topic/**} 등)는 거절한다. 예전에는 방 토픽이 아닌 목적지를 그대로 통과시켜
 *       {@code /topic/**} 구독 한 번으로 모든 방의 대화를 받을 수 있었다.</li>
 *   <li>{@code SEND} — {@code /app/}으로 시작하는 목적지만. 브로커 목적지({@code /topic}·{@code /queue})로
 *       직접 SEND하면 simple broker가 검사 없이 구독자에게 뿌리므로 senderId를 위조할 수 있었다.</li>
 *   <li>그 밖의 프레임 — 인증된 세션에서만. {@code DISCONNECT}만 예외다(연결을 끝낼 뿐이라 막을 이유가 없다).</li>
 * </ul>
 *
 * <p>인증과 인가는 다르다. 연결 프레임에서 "누구인지"를 확인하고, SUBSCRIBE에서 "이 방을 볼 자격이
 * 있는지"를 따로 본다. 전자만 하면 로그인한 아무나 남의 방을 엿볼 수 있다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomMembershipGate membershipGate;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // wrap()이 아니라 getAccessor()를 쓴다.
        // StompHeaderAccessor.wrap(message)는 헤더 "복사본"에 대한 접근자를 만들기 때문에
        // 거기에 setUser()를 해도 원본 메시지에 반영되지 않는다. 인증이 조용히 사라진다.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            // 클라이언트 인바운드 채널의 메시지는 전부 StompSubProtocolHandler가 만든 StompHeaderAccessor다.
            // 그렇지 않은 메시지가 들어왔다면 판단할 근거가 없으므로 막는다.
            throw reject("알 수 없는 프레임입니다.");
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            // 하트비트. 프레임이 아니다.
            return message;
        }

        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case DISCONNECT -> {
                // 인증 여부와 관계없이 연결을 끝내는 프레임이다.
            }
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> authorizeSend(accessor);
            default -> currentUserId(accessor);   // UNSUBSCRIBE·ACK·NACK·BEGIN·COMMIT·ABORT
        }
        return message;
    }

    /**
     * 연결 프레임의 {@code Authorization} 네이티브 헤더로 세션을 인증한다.
     * 여기서 예외가 나가면 연결 자체가 수립되지 않는다.
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw reject("WebSocket 연결에는 Authorization 헤더가 필요합니다.");
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw reject("WebSocket 연결에는 Authorization 헤더가 필요합니다.");
        }

        Authentication authentication;
        try {
            if (!jwtTokenProvider.validateAccessToken(token)) {
                throw reject("유효하지 않은 토큰입니다.");
            }
            authentication = jwtTokenProvider.getAuthentication(token);
        } catch (BusinessException e) {
            // 만료·서명 불일치·탈퇴 사용자 등. 원인을 클라이언트에 그대로 흘리지 않는다.
            throw reject("유효하지 않은 토큰입니다.");
        }

        // StompSubProtocolHandler가 연결 프레임(CONNECT·STOMP)에서 이 값을 세션에 기억해
        // 이후 모든 프레임에 붙인다. SecurityContextChannelInterceptor가 이걸 읽어 SecurityContextHolder에
        // 옮기고, 그래야 @MessageMapping 핸들러의 @AuthenticationPrincipal이 채워진다.
        accessor.setUser(authentication);
    }

    /**
     * {@code /topic/rooms/{roomId}} 구독만, 그 방의 활성 멤버만 허용한다.
     *
     * <p>{@code leftAt IS NULL} 조건이 핵심이다. 나갔거나 강퇴당한 사람도 roomId를 알고 있으므로,
     * 멤버 행의 존재만 보면 탈퇴 후에도 계속 구독할 수 있다.
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        UUID userId = currentUserId(accessor);

        String destination = accessor.getDestination();
        UUID roomId = ChatRoomDestinations.parseRoomIdOrNull(destination);
        if (roomId == null) {
            throw reject("구독할 수 없는 경로입니다: " + destination);
        }

        // 판정은 게이트 하나에만 있다. 여기와 배달 시점(ChatDeliveryChannelInterceptor)이
        // 각자 조건을 들고 있으면 한쪽만 고쳤을 때 조용히 어긋난다.
        if (!membershipGate.isActiveMember(userId, roomId)) {
            throw reject("채팅방의 참여자가 아닙니다.");
        }
    }

    /**
     * 클라이언트 SEND는 {@code @MessageMapping} 핸들러로만 보낸다.
     * 핸들러({@code MessageService})가 발신자 멤버십을 확인하고 senderId를 Principal에서 채운다.
     */
    private void authorizeSend(StompHeaderAccessor accessor) {
        currentUserId(accessor);
        String destination = accessor.getDestination();
        if (!ChatRoomDestinations.isApplicationDestination(destination)) {
            throw reject("보낼 수 없는 경로입니다: " + destination);
        }
    }

    private UUID currentUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getUserId();
        }
        throw reject("인증되지 않은 연결입니다.");
    }

    private MessagingException reject(String reason) {
        return new MessagingException(reason);
    }
}
