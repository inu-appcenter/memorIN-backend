package com.memorin.global.config;

import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP 프레임의 인증(CONNECT)과 구독 인가(SUBSCRIBE)를 처리한다.
 *
 * <p>HTTP 필터({@code JwtAuthenticationFilter})로는 STOMP를 지킬 수 없다. 필터는 핸드셰이크 요청
 * 한 번만 지나가고, 그 뒤 같은 TCP 연결로 흐르는 STOMP 프레임은 서블릿 필터 체인을 타지 않는다.
 * 그래서 인증 지점을 CONNECT 프레임으로 옮긴다. (docs/sprint4-architecture-review.md §2)
 *
 * <p>이 클래스가 생기기 전에는 {@code /ws/**}가 permitAll이고 구독 검사가 없어
 * <b>토큰 없이 연결해 아무 방이나 구독하면 그 방의 모든 메시지를 실시간으로 받을 수 있었다.</b>
 * 발신(SEND)은 {@code MessageService}가 이미 활성 멤버인지 확인하고 있었으므로, 뚫려 있던 것은
 * 수신 경로였다.
 *
 * <p>인증과 인가는 다르다. CONNECT에서 "누구인지"를 확인하고, SUBSCRIBE에서 "이 방을 볼 자격이
 * 있는지"를 따로 본다. 전자만 하면 로그인한 아무나 남의 방을 엿볼 수 있다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** 방 단위 브로드캐스트 목적지. MessageController가 여기로 보낸다. */
    static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // wrap()이 아니라 getAccessor()를 쓴다.
        // StompHeaderAccessor.wrap(message)는 헤더 "복사본"에 대한 접근자를 만들기 때문에
        // 거기에 setUser()를 해도 원본 메시지에 반영되지 않는다. 인증이 조용히 사라진다.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            default -> {
                // SEND·UNSUBSCRIBE·DISCONNECT 등은 여기서 다루지 않는다.
                // SEND는 MessageService가 방별 활성 멤버 여부를 이미 검사한다.
            }
        }
        return message;
    }

    /**
     * CONNECT 프레임의 {@code Authorization} 네이티브 헤더로 세션을 인증한다.
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
            if (!jwtTokenProvider.validateToken(token)) {
                throw reject("유효하지 않은 토큰입니다.");
            }
            authentication = jwtTokenProvider.getAuthentication(token);
        } catch (BusinessException e) {
            // 만료·서명 불일치·탈퇴 사용자 등. 원인을 클라이언트에 그대로 흘리지 않는다.
            throw reject("유효하지 않은 토큰입니다.");
        }

        // 이 세션의 이후 모든 프레임에 Principal이 따라붙는다.
        // SecurityContextChannelInterceptor가 이걸 읽어 SecurityContextHolder에 옮기고,
        // 그래야 @MessageMapping 핸들러의 @AuthenticationPrincipal이 채워진다.
        accessor.setUser(authentication);
    }

    /**
     * {@code /topic/rooms/{roomId}} 구독을 방의 활성 멤버로 제한한다.
     *
     * <p>{@code leftAt IS NULL} 조건이 핵심이다. 나갔거나 강퇴당한 사람도 roomId를 알고 있으므로,
     * 멤버 행의 존재만 보면 탈퇴 후에도 계속 구독할 수 있다.
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            // 방 토픽이 아닌 목적지는 이 인터셉터의 관심사가 아니다.
            return;
        }

        UUID roomId = parseRoomId(destination);
        UUID userId = currentUserId(accessor);

        if (!chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)) {
            throw reject("채팅방의 참여자가 아닙니다.");
        }
    }

    private UUID parseRoomId(String destination) {
        String raw = destination.substring(ROOM_TOPIC_PREFIX.length());
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw reject("잘못된 구독 경로입니다: " + destination);
        }
    }

    private UUID currentUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getUserId();
        }
        // CONNECT에서 인증됐다면 여기 올 수 없다. 방어적으로 막는다.
        throw reject("인증되지 않은 연결입니다.");
    }

    private MessagingException reject(String reason) {
        return new MessagingException(reason);
    }
}
