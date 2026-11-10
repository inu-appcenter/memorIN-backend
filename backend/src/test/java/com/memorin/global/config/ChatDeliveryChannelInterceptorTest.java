package com.memorin.global.config;

import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 배달 직전 인가 검증 (#210, #304).
//
// SUBSCRIBE 검사는 "구독을 요청하는 순간"만 본다. 이미 맺어진 구독은 강퇴·나가기 뒤에도
// 살아 있고, InMemory 브로커는 구독 레지스트리만 보고 뿌린다. 이 인터셉터가 그 창을 닫는다.
// preSend가 null을 반환하면 그 세션으로는 나가지 않는다.
//
// #304: 예전 테스트는 StompHeaderAccessor(MESSAGE) + simpUser로 메시지를 만들었다. 실제 simple broker가
// 내보내는 메시지는 그런 모양이 아니라(SimpMessageHeaderAccessor, 받는 사람은 simpSessionId로만 식별)
// 운영에서는 인터셉터가 항상 통과시키는데 테스트만 통과하고 있었다. 여기서는 브로커와 같은 모양으로 만든다.
class ChatDeliveryChannelInterceptorTest {

    private static final String ROOM_TOPIC = "/topic/rooms/";
    private static final String SESSION_ID = "session-1";

    private ChatRoomMemberRepository chatRoomMemberRepository;
    private WebSocketSessionRegistry sessionRegistry;
    private ChatDeliveryChannelInterceptor interceptor;
    private MessageChannel channel;

    private UUID userId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        chatRoomMemberRepository = mock(ChatRoomMemberRepository.class);
        sessionRegistry = mock(WebSocketSessionRegistry.class);
        interceptor = new ChatDeliveryChannelInterceptor(
            new ChatRoomMembershipGate(chatRoomMemberRepository), sessionRegistry);
        channel = mock(MessageChannel.class);

        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        given(sessionRegistry.userIdOf(SESSION_ID)).willReturn(userId);
    }

    // SimpleBrokerMessageHandler.sendMessageToSubscribers와 같은 모양.
    // simpUser에는 받는 사람이 아니라 발신 메시지의 값(여기서는 다른 사용자)이 복사돼 있을 수 있다.
    private Message<?> brokerMessage(String destination, String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId("sub-0");
        UserDetailsImpl someoneElse = mock(UserDetailsImpl.class);
        given(someoneElse.getUserId()).willReturn(UUID.randomUUID());
        Authentication sender = new UsernamePasswordAuthenticationToken(someoneElse, null, List.of());
        accessor.setUser(sender);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> send(Message<?> message) {
        return interceptor.preSend(message, channel);
    }

    @Test
    void 활성_멤버의_세션에는_그대로_배달된다() {
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(true);

        Message<?> message = brokerMessage(ROOM_TOPIC + roomId, SESSION_ID);

        assertThat(send(message)).isSameAs(message);
    }

    // #210이 말하는 바로 그 상황이다. 구독은 이미 맺어져 있고, 그 사이에 강퇴당해 left_at이 찍혔다.
    @Test
    void 강퇴당한_뒤에는_이미_맺은_구독으로도_메시지가_가지_않는다() {
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(false);

        assertThat(send(brokerMessage(ROOM_TOPIC + roomId, SESSION_ID)))
            .as("활성 멤버가 아니면 이 세션으로 나가면 안 된다")
            .isNull();
    }

    // simpUser가 아니라 세션으로 받는 사람을 찾는지 본다. simpUser를 읽으면 보낸 사람 기준으로 판정된다.
    @Test
    void 받는_사람은_simpUser가_아니라_세션으로_판정한다() {
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(false);

        assertThat(send(brokerMessage(ROOM_TOPIC + roomId, SESSION_ID))).isNull();
    }

    @Test
    void 사용자를_알_수_없는_세션에는_방_메시지가_가지_않는다() {
        assertThat(send(brokerMessage(ROOM_TOPIC + roomId, "unknown-session")))
            .as("인증되지 않은 세션에 대화가 흘러가면 안 된다")
            .isNull();
    }

    @Test
    void 방_토픽이_아닌_목적지는_건드리지_않는다() {
        Message<?> message = brokerMessage("/topic/notices", SESSION_ID);

        assertThat(send(message)).isSameAs(message);
    }

    // MESSAGE 외의 메시지(CONNECT_ACK·HEARTBEAT 등)까지 거르면 연결 자체가 깨진다.
    @Test
    void MESSAGE가_아닌_메시지는_건드리지_않는다() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
        accessor.setSessionId("unknown-session");
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(send(message)).isSameAs(message);
    }
}
