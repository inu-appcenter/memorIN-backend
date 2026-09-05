package com.memorin.global.config;

import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;

// InMemory 브로커의 세션 누수 방지 설정(docs/sprint4-architecture-review.md §3)이
// 실제로 걸려 있는지 본다.
//
// 이 테스트가 지키는 것은 "숫자가 10초냐"가 아니라 **제한이 존재한다는 사실**이다.
// 하트비트를 지우면 DISCONNECT 없이 사라진 클라이언트를 서버가 영영 못 알아채고,
// 버퍼/시간 제한을 지우면 느린 수신자 하나가 힙을 밀어 올린다. 둘 다 부하가 걸리기 전까지
// 아무 증상이 없어서, 리팩터링 중에 조용히 사라져도 눈치채지 못한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WebSocketBrokerConfigTest extends PostgresTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private WebSocketSessionRegistry sessionRegistry;

    @Test
    void 하트비트는_스케줄러와_함께_설정된다() {
        SimpleBrokerMessageHandler broker =
            context.getBean("simpleBrokerMessageHandler", SimpleBrokerMessageHandler.class);

        assertThat(broker.getHeartbeatValue()).containsExactly(10_000L, 10_000L);
        // 스케줄러 없이 하트비트만 켜면 기동 시 실패한다. 둘은 항상 같이 간다.
        assertThat(broker.getTaskScheduler()).isNotNull();
    }

    @Test
    void 세션_전송_버퍼와_시간_제한이_걸려_있다() {
        SubProtocolWebSocketHandler handler =
            context.getBean("subProtocolWebSocketHandler", SubProtocolWebSocketHandler.class);

        assertThat(handler.getSendTimeLimit()).isEqualTo(10 * 1000);
        assertThat(handler.getSendBufferSizeLimit()).isEqualTo(512 * 1024);
        assertThat(handler.getTimeToFirstMessage()).isEqualTo(30 * 1000);
    }

    @Test
    void 세션_카운터가_연결과_종료를_반영한다() {
        int before = sessionRegistry.activeCount();

        eventPublisher.publishEvent(new SessionConnectedEvent(this, stompMessage(StompCommand.CONNECTED, "s1")));
        assertThat(sessionRegistry.activeCount()).isEqualTo(before + 1);

        eventPublisher.publishEvent(new SessionDisconnectEvent(
            this, stompMessage(StompCommand.DISCONNECT, "s1"), "s1", CloseStatus.NORMAL));
        assertThat(sessionRegistry.activeCount()).isEqualTo(before);
    }

    @Test
    void 연결된적_없는_세션이_끊겨도_카운터는_음수가_되지_않는다() {
        // CONNECT를 보내지 않고 끊긴 소켓에도 DISCONNECT는 올라온다.
        eventPublisher.publishEvent(new SessionDisconnectEvent(
            this, stompMessage(StompCommand.DISCONNECT, "ghost"), "ghost", CloseStatus.NO_STATUS_CODE));

        assertThat(sessionRegistry.activeCount()).isNotNegative();
    }

    private Message<byte[]> stompMessage(StompCommand command, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
