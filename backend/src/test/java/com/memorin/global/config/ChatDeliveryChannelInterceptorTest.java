package com.memorin.global.config;

import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 배달 직전 인가 검증 (#210).
//
// SUBSCRIBE 검사는 "구독을 요청하는 순간"만 본다. 이미 맺어진 구독은 강퇴·나가기 뒤에도
// 살아 있고, InMemory 브로커는 구독 레지스트리만 보고 뿌린다. 그래서 강퇴당한 직후부터
// 그 사람이 탭을 닫을 때까지 남의 대화가 계속 흘러갔다.
//
// 이 인터셉터는 그 창을 닫는다. preSend가 null을 반환하면 그 세션으로는 나가지 않는다.
class ChatDeliveryChannelInterceptorTest {

    private static final String ROOM_TOPIC = "/topic/rooms/";

    private ChatRoomMemberRepository chatRoomMemberRepository;
    private ChatDeliveryChannelInterceptor interceptor;
    private MessageChannel channel;

    private UUID userId;
    private UUID roomId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        chatRoomMemberRepository = mock(ChatRoomMemberRepository.class);
        interceptor = new ChatDeliveryChannelInterceptor(new ChatRoomMembershipGate(chatRoomMemberRepository));
        channel = mock(MessageChannel.class);

        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        authentication = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    }

    // 브로커가 구독자에게 내보내는 프레임은 MESSAGE다.
    private Message<?> outbound(String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(authentication);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> send(Message<?> message) {
        return interceptor.preSend(message, channel);
    }

    @Test
    void 활성_멤버에게는_그대로_배달된다() {
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(true);

        Message<?> message = outbound(ROOM_TOPIC + roomId, true);

        assertThat(send(message)).isSameAs(message);
    }

    // 이 테스트가 이 파일의 핵심이다 — #210이 말하는 바로 그 상황이다.
    // 구독은 이미 맺어져 있고, 그 사이에 강퇴당해 left_at이 찍혔다.
    @Test
    void 강퇴당한_뒤에는_이미_맺은_구독으로도_메시지가_가지_않는다() {
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(false);

        assertThat(send(outbound(ROOM_TOPIC + roomId, true)))
            .as("활성 멤버가 아니면 이 세션으로 나가면 안 된다")
            .isNull();
    }

    @Test
    void Principal이_없는_세션에는_방_메시지가_가지_않는다() {
        assertThat(send(outbound(ROOM_TOPIC + roomId, false)))
            .as("인증되지 않은 세션에 대화가 흘러가면 안 된다")
            .isNull();
    }

    // 방 토픽이 아닌 목적지는 우리가 만든 것이 아니므로 판단하지 않는다.
    // 여기서 막으면 나중에 다른 토픽을 추가할 때 조용히 메시지가 사라진다.
    @Test
    void 방_토픽이_아닌_목적지는_건드리지_않는다() {
        Message<?> message = outbound("/topic/notices", true);

        assertThat(send(message)).isSameAs(message);
    }

    @Test
    void 형식이_잘못된_방_경로는_건드리지_않는다() {
        Message<?> message = outbound(ROOM_TOPIC + "not-a-uuid", true);

        assertThat(send(message)).isSameAs(message);
    }

    // MESSAGE 외의 프레임(CONNECTED·RECEIPT·ERROR)까지 거르면 연결 자체가 깨진다.
    @Test
    void MESSAGE가_아닌_프레임은_건드리지_않는다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(send(message)).isSameAs(message);
    }
}
