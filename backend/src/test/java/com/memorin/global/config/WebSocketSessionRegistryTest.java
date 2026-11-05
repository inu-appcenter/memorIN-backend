package com.memorin.global.config;

import com.memorin.global.exception.UserDetailsImpl;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class WebSocketSessionRegistryTest {

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    private final UUID userId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final Principal principal = principal(userId);

    @Test
    void 연결만_된_세션은_방을_구독한_것으로_보지_않는다() {
        connect("s1");

        assertThat(registry.isSubscribedToRoom(userId, roomId)).isFalse();
    }

    @Test
    void 방_토픽을_구독하면_true_구독을_해제하면_false() {
        connect("s1");
        subscribe("s1", "sub-0", "/topic/rooms/" + roomId);

        assertThat(registry.isSubscribedToRoom(userId, roomId)).isTrue();
        assertThat(registry.isSubscribedToRoom(userId, UUID.randomUUID())).isFalse();

        unsubscribe("s1", "sub-0");

        assertThat(registry.isSubscribedToRoom(userId, roomId)).isFalse();
    }

    @Test
    void 다른_기기_세션이_방을_구독_중이면_true() {
        connect("web");
        connect("phone");
        subscribe("phone", "sub-0", "/topic/rooms/" + roomId);

        assertThat(registry.isSubscribedToRoom(userId, roomId)).isTrue();
    }

    @Test
    void 세션이_끊기면_구독도_정리된다() {
        connect("s1");
        subscribe("s1", "sub-0", "/topic/rooms/" + roomId);

        registry.onSessionDisconnected(new SessionDisconnectEvent(
            this, message(StompCommand.DISCONNECT, "s1", null, null), "s1", CloseStatus.NORMAL, principal));

        assertThat(registry.isSubscribedToRoom(userId, roomId)).isFalse();
    }

    @Test
    void 방_토픽이_아닌_구독은_무시한다() {
        connect("s1");
        subscribe("s1", "sub-0", "/user/queue/errors");

        assertThat(registry.isSubscribedToRoom(userId, roomId)).isFalse();
    }

    private void connect(String sessionId) {
        registry.onSessionConnected(new SessionConnectedEvent(
            this, message(StompCommand.CONNECTED, sessionId, null, null), principal));
    }

    private void subscribe(String sessionId, String subscriptionId, String destination) {
        registry.onSessionSubscribe(new SessionSubscribeEvent(
            this, message(StompCommand.SUBSCRIBE, sessionId, subscriptionId, destination), principal));
    }

    private void unsubscribe(String sessionId, String subscriptionId) {
        registry.onSessionUnsubscribe(new SessionUnsubscribeEvent(
            this, message(StompCommand.UNSUBSCRIBE, sessionId, subscriptionId, null), principal));
    }

    private Message<byte[]> message(StompCommand command, String sessionId, String subscriptionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (subscriptionId != null) {
            accessor.setSubscriptionId(subscriptionId);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Principal principal(UUID userId) {
        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        return new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    }
}
