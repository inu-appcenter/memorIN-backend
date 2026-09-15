package com.memorin.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

// 현재 살아 있는 STOMP 세션 수를 센다.
//
// InMemory 브로커는 세션이 반환되지 않으면 그대로 힙에 쌓인다. "탭을 닫으면 세션이 즉각 반환된다"가
// Sprint 4 게이트 항목이므로, 데모 때 눈으로 확인할 수 있는 값이 하나는 있어야 한다.
// W9 스트레스 테스트(docs/sprint4-architecture-review.md §6)도 이 값을 측정 훅으로 쓴다.
//
// 액추에이터를 붙이기 전까지는 로그가 유일한 노출 창구다.
@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final AtomicInteger activeSessions = new AtomicInteger();

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        log.info("WS 세션 연결. active={}", activeSessions.incrementAndGet());
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        // CONNECT를 보내지 않고 끊긴 소켓에도 DISCONNECT 이벤트가 올 수 있어 음수로 내려가지 않게 막는다.
        int active = activeSessions.updateAndGet(current -> Math.max(0, current - 1));
        log.info("WS 세션 종료. status={}, active={}", event.getCloseStatus(), active);
    }

    public int activeCount() {
        return activeSessions.get();
    }
}
