package com.memorin.global.config;

import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// STOMP 인증·인가 인터셉터 검증.
//
// 이 경로는 HTTP가 아니라 메시지 채널이라 MockMvc로 잡을 수 없다. 인터셉터에 프레임을 직접
// 넣어 확인한다.
//
// 검증의 핵심은 두 가지다.
//  - CONNECT: 토큰이 없거나 유효하지 않으면 연결이 성립하면 안 된다
//  - SUBSCRIBE: 인증만으로는 부족하다. 로그인한 사용자라도 그 방의 활성 멤버가 아니면 막혀야 한다
//
// 특히 "나갔거나 강퇴당한 사람"이 막히는지가 중요하다. roomId를 이미 알고 있기 때문에
// leftAt을 보지 않으면 탈퇴 후에도 계속 남의 대화를 받는다.
class StompAuthChannelInterceptorTest {

    private static final String ROOM_TOPIC = "/topic/rooms/";

    private JwtTokenProvider jwtTokenProvider;
    private ChatRoomMemberRepository chatRoomMemberRepository;
    private StompAuthChannelInterceptor interceptor;
    private MessageChannel channel;

    private UUID userId;
    private UUID roomId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        chatRoomMemberRepository = mock(ChatRoomMemberRepository.class);
        // 게이트는 목이 아니라 실물을 쓴다. 멤버 판정이 실제로 게이트를 지나가는지까지 봐야 한다.
        interceptor = new StompAuthChannelInterceptor(
            jwtTokenProvider, new ChatRoomMembershipGate(chatRoomMemberRepository));
        channel = mock(MessageChannel.class);

        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        authentication = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    }

    private StompHeaderAccessor connectFrame(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return accessor;
    }

    private StompHeaderAccessor subscribeFrame(String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(authentication);
        }
        return accessor;
    }

    // setLeaveMutable(true)가 없으면 getMessageHeaders()가 accessor를 잠가서
    // 인터셉터의 setUser()가 "Already immutable"로 터진다.
    //
    // 운영 경로도 같은 방식이다 — StompSubProtocolHandler가 클라이언트 프레임을 메시지로 만들 때
    // setLeaveMutable(true)를 걸어준다. 그래서 preSend에서 Principal을 붙일 수 있다.
    private Message<byte[]> toMessage(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void send(StompHeaderAccessor accessor) {
        interceptor.preSend(toMessage(accessor), channel);
    }

    // ---- CONNECT ----

    @Test
    void 토큰이_없으면_연결이_거부된다() {
        assertThatThrownBy(() -> send(connectFrame(null)))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("Authorization");
    }

    @Test
    void Bearer가_아닌_헤더는_거부된다() {
        assertThatThrownBy(() -> send(connectFrame("some-raw-token")))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void 유효하지_않은_토큰은_거부된다() {
        given(jwtTokenProvider.validateAccessToken("bad")).willReturn(false);

        assertThatThrownBy(() -> send(connectFrame("Bearer bad")))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("유효하지 않은");
    }

    // 만료 토큰은 validateToken이 BusinessException을 던진다(JwtAuthenticationFilter와 같은 계약).
    // 그 예외가 그대로 새어나가면 클라이언트가 받는 에러가 제각각이 된다.
    @Test
    void 만료된_토큰도_같은_방식으로_거부된다() {
        given(jwtTokenProvider.validateAccessToken("expired"))
            .willThrow(new BusinessException(ErrorCode.AUTH_003));

        assertThatThrownBy(() -> send(connectFrame("Bearer expired")))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("유효하지 않은");
    }

    @Test
    void 유효한_토큰이면_세션에_사용자가_붙는다() {
        given(jwtTokenProvider.validateAccessToken("good")).willReturn(true);
        given(jwtTokenProvider.getAuthentication("good")).willReturn(authentication);

        StompHeaderAccessor accessor = connectFrame("Bearer good");
        send(accessor);

        // 이후 프레임(SUBSCRIBE·SEND)이 이 Principal을 물려받는다.
        // wrap()으로 만든 복사본에 setUser를 하면 여기가 null이 된다.
        assertThat(accessor.getUser()).isSameAs(authentication);
    }

    // ---- SUBSCRIBE ----

    @Test
    void 방의_활성_멤버는_구독할_수_있다() {
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(true);

        assertThatCode(() -> send(subscribeFrame(ROOM_TOPIC + roomId, true)))
            .doesNotThrowAnyException();
    }

    @Test
    void 멤버가_아니면_구독이_거부된다() {
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(false);

        assertThatThrownBy(() -> send(subscribeFrame(ROOM_TOPIC + roomId, true)))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("참여자가 아닙니다");
    }

    // 이 테스트가 이 파일의 핵심이다.
    // 나간·강퇴당한 사람은 roomId를 알고 있으므로, leftAt을 보지 않으면 탈퇴 후에도 계속 수신한다.
    @Test
    void 나갔거나_강퇴당한_사람은_구독할_수_없다() {
        // leftAt이 찍힌 멤버 → 활성 멤버 조회는 false
        given(chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId))
            .willReturn(false);

        assertThatThrownBy(() -> send(subscribeFrame(ROOM_TOPIC + roomId, true)))
            .isInstanceOf(MessagingException.class);

        // 예전에는 여기서 "leftAt을 무시하는 변형(existsByRoomIdAndUserId)을 쓰지 않았다"를
        // verify(never())로 확인했다. 그 변형 자체를 리포지토리에서 없앴으므로
        // 이제는 잘못 고를 방법이 없다 — 컴파일이 안 된다. 검증이 테스트에서 타입으로 옮겨갔다.
    }

    @Test
    void 인증되지_않은_연결은_구독할_수_없다() {
        assertThatThrownBy(() -> send(subscribeFrame(ROOM_TOPIC + roomId, false)))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("인증되지 않은");

        verify(chatRoomMemberRepository, never())
            .existsByRoom_IdAndUser_IdAndLeftAtIsNull(any(), any());
    }

    @Test
    void 잘못된_형식의_방_경로는_거부된다() {
        assertThatThrownBy(() -> send(subscribeFrame(ROOM_TOPIC + "not-a-uuid", true)))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("구독할 수 없는 경로");
    }

    // #304: 예전에는 방 토픽이 아닌 목적지를 "관심 밖"이라며 그대로 통과시켰고, 이 테스트가 그 동작을
    // 고정하고 있었다. /topic/** 같은 패턴 구독 한 번으로 모든 방의 대화를 받을 수 있었다.
    // 이제는 /topic/rooms/{uuid} 외의 구독을 모두 거절한다.
    @ParameterizedTest
    @ValueSource(strings = {
        "/topic/notifications",
        "/topic/**",
        "/topic/rooms/*",
        "/topic/rooms/**",
        "/topic/rooms/{id}",
        "/queue/anything",
        "/user/queue/errors",
        "/app/chat.sendText",
    })
    void 방_토픽이_아닌_목적지와_패턴_목적지는_거부된다(String destination) {
        assertThatThrownBy(() -> send(subscribeFrame(destination, true)))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("구독할 수 없는 경로");

        verify(chatRoomMemberRepository, never())
            .existsByRoom_IdAndUser_IdAndLeftAtIsNull(any(), eq(userId));
    }

    // UUID.fromString은 비정규 표기도 받아들인다. 정규 표기와 정확히 같을 때만 받는다.
    @Test
    void 비정규_UUID_표기는_거부된다() {
        assertThatThrownBy(() -> send(subscribeFrame(ROOM_TOPIC + "1-1-1-1-1", true)))
            .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> send(subscribeFrame(ROOM_TOPIC + roomId.toString().toUpperCase(), true)))
            .isInstanceOf(MessagingException.class);
    }

    // ---- STOMP 프레임 (#304) ----

    @Test
    void STOMP_프레임도_CONNECT와_똑같이_토큰을_요구한다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.STOMP);

        assertThatThrownBy(() -> send(accessor))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("Authorization");
    }

    @Test
    void STOMP_프레임에_유효한_토큰이면_세션에_사용자가_붙는다() {
        given(jwtTokenProvider.validateAccessToken("good")).willReturn(true);
        given(jwtTokenProvider.getAuthentication("good")).willReturn(authentication);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.STOMP);
        accessor.setNativeHeader("Authorization", "Bearer good");

        send(accessor);

        assertThat(accessor.getUser()).isSameAs(authentication);
    }

    // ---- SEND (#304) ----

    private StompHeaderAccessor sendFrame(String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(authentication);
        }
        return accessor;
    }

    @Test
    void 앱_목적지로의_SEND는_통과한다() {
        assertThatCode(() -> send(sendFrame("/app/chat.sendText", true)))
            .doesNotThrowAnyException();
    }

    // 브로커 목적지로 직접 보내면 simple broker가 MessageService를 거치지 않고 구독자에게 뿌린다.
    // 본문의 senderId를 마음대로 적어 다른 사람인 척할 수 있었다.
    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms/00000000-0000-0000-0000-000000000001", "/queue/x", "/user/queue/x", "app/chat.sendText"})
    void 브로커_목적지로의_직접_SEND는_거부된다(String destination) {
        assertThatThrownBy(() -> send(sendFrame(destination, true)))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("보낼 수 없는 경로");
    }

    @Test
    void 인증되지_않은_세션의_SEND는_거부된다() {
        assertThatThrownBy(() -> send(sendFrame("/app/chat.sendText", false)))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("인증되지 않은");
    }

    @Test
    void 인증되지_않은_세션의_UNSUBSCRIBE도_거부되고_DISCONNECT는_통과한다() {
        StompHeaderAccessor unsubscribe = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        unsubscribe.setSubscriptionId("sub-0");
        assertThatThrownBy(() -> send(unsubscribe)).isInstanceOf(MessagingException.class);

        assertThatCode(() -> send(StompHeaderAccessor.create(StompCommand.DISCONNECT)))
            .doesNotThrowAnyException();
    }
}
