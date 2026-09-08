package com.memorin.global.config;

import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        interceptor = new StompAuthChannelInterceptor(jwtTokenProvider, chatRoomMemberRepository);
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
        given(jwtTokenProvider.validateToken("bad")).willReturn(false);

        assertThatThrownBy(() -> send(connectFrame("Bearer bad")))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("유효하지 않은");
    }

    // 만료 토큰은 validateToken이 BusinessException을 던진다(JwtAuthenticationFilter와 같은 계약).
    // 그 예외가 그대로 새어나가면 클라이언트가 받는 에러가 제각각이 된다.
    @Test
    void 만료된_토큰도_같은_방식으로_거부된다() {
        given(jwtTokenProvider.validateToken("expired"))
            .willThrow(new BusinessException(ErrorCode.AUTH_003));

        assertThatThrownBy(() -> send(connectFrame("Bearer expired")))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("유효하지 않은");
    }

    @Test
    void 유효한_토큰이면_세션에_사용자가_붙는다() {
        given(jwtTokenProvider.validateToken("good")).willReturn(true);
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

        // leftAt을 무시하는 쪽(existsByRoomIdAndUserId)을 쓰면 이 검증이 무의미해진다.
        verify(chatRoomMemberRepository, never()).existsByRoomIdAndUserId(any(), any());
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
            .hasMessageContaining("잘못된 구독 경로");
    }

    // 방 토픽이 아닌 목적지까지 막으면 알림 등 다른 구독이 죽는다.
    @Test
    void 방_토픽이_아닌_목적지는_그대로_통과한다() {
        assertThatCode(() -> send(subscribeFrame("/topic/notifications", true)))
            .doesNotThrowAnyException();

        verify(chatRoomMemberRepository, never())
            .existsByRoom_IdAndUser_IdAndLeftAtIsNull(any(), eq(userId));
    }
}
