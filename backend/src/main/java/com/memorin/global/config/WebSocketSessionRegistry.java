package com.memorin.global.config;

import com.memorin.global.exception.UserDetailsImpl;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, UUID> usersBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<String>> sessionIdsByUserId = new ConcurrentHashMap<>();
    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();
    // sessionId -> (subscriptionId -> roomId). UNSUBSCRIBE 프레임에는 destination이 없어 구독 id로 찾는다.
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, UUID>> roomSubscriptionsBySessionId =
        new ConcurrentHashMap<>();
    // STOMP 세션 ID == WebSocketSession ID(SockJS면 SockJS 세션 ID). 서버가 연결을 끊을 때 쓴다(#304).
    private final ConcurrentHashMap<String, WebSocketSession> transportSessions = new ConcurrentHashMap<>();

    /** 강제로 끊을 때 보내는 종료 코드. 클라이언트는 재연결하고, 재연결 시 다시 인증·인가를 거친다. */
    static final CloseStatus REVOKED = CloseStatus.POLICY_VIOLATION.withReason("session revoked");

    void onTransportOpened(WebSocketSession session) {
        transportSessions.put(session.getId(), session);
    }

    void onTransportClosed(WebSocketSession session) {
        transportSessions.remove(session.getId(), session);
    }

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        String sessionId = sessionIdOf(event.getMessage().getHeaders());
        UUID userId = userIdOf(event.getUser());
        if (sessionId == null) {
            return;
        }

        activeSessionIds.add(sessionId);
        if (userId == null) {
            return;
        }
        usersBySessionId.put(sessionId, userId);
        sessionIdsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.info("WS session connected. userId={}, activeSessions={}", userId, activeCount());
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        String sessionId = sessionIdOf(event.getMessage().getHeaders());
        if (sessionId == null) {
            return;
        }

        activeSessionIds.remove(sessionId);
        roomSubscriptionsBySessionId.remove(sessionId);
        UUID userId = usersBySessionId.remove(sessionId);
        if (userId == null) {
            return;
        }

        sessionIdsByUserId.computeIfPresent(userId, (ignored, sessionIds) -> {
            sessionIds.remove(sessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
        log.info("WS session disconnected. userId={}, activeSessions={}", userId, activeCount());
    }

    @EventListener
    public void onSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        UUID roomId = ChatRoomDestinations.parseRoomIdOrNull(accessor.getDestination());
        if (sessionId == null || subscriptionId == null || roomId == null) {
            return;
        }
        roomSubscriptionsBySessionId
            .computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
            .put(subscriptionId, roomId);
    }

    @EventListener
    public void onSessionUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        roomSubscriptionsBySessionId.computeIfPresent(sessionId, (ignored, subscriptions) -> {
            subscriptions.remove(subscriptionId);
            return subscriptions.isEmpty() ? null : subscriptions;
        });
    }

    public boolean isSubscribedToRoom(UUID userId, UUID roomId) {
        Set<String> sessionIds = sessionIdsByUserId.get(userId);
        if (sessionIds == null || roomId == null) {
            return false;
        }
        for (String sessionId : sessionIds) {
            Map<String, UUID> subscriptions = roomSubscriptionsBySessionId.get(sessionId);
            if (subscriptions != null && subscriptions.containsValue(roomId)) {
                return true;
            }
        }
        return false;
    }

    /** 이 사용자의 열린 세션을 모두 끊는다. 로그아웃·탈퇴. 끊은 세션 수를 돌려준다. */
    public int closeSessionsOf(UUID userId) {
        Set<String> sessionIds = sessionIdsByUserId.get(userId);
        if (sessionIds == null) {
            return 0;
        }
        return closeAll(Set.copyOf(sessionIds));
    }

    /**
     * 이 사용자의 세션 중 그 방을 구독 중인 것만 끊는다. 강퇴.
     *
     * <p>배달 필터가 이미 그 방 메시지를 막지만, 세션을 남겨 두면 강퇴 사실이 클라이언트에 전달되지 않는다.
     * STOMP에는 서버가 보내는 UNSUBSCRIBE가 없어 연결을 끊는 것이 유일한 신호다. 같은 세션의 다른 방 구독도
     * 함께 끊기므로 클라이언트는 재연결해서 남은 방을 다시 구독해야 한다.
     */
    public int closeSessionsSubscribedTo(UUID userId, UUID roomId) {
        Set<String> sessionIds = sessionIdsByUserId.get(userId);
        if (sessionIds == null || roomId == null) {
            return 0;
        }
        Set<String> targets = new java.util.HashSet<>();
        for (String sessionId : sessionIds) {
            Map<String, UUID> subscriptions = roomSubscriptionsBySessionId.get(sessionId);
            if (subscriptions != null && subscriptions.containsValue(roomId)) {
                targets.add(sessionId);
            }
        }
        return closeAll(targets);
    }

    private int closeAll(Set<String> sessionIds) {
        int closed = 0;
        for (String sessionId : sessionIds) {
            WebSocketSession session = transportSessions.get(sessionId);
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.close(REVOKED);
                closed++;
            } catch (Exception e) {
                // 이미 끊기는 중인 세션. 정리는 SessionDisconnectEvent가 한다.
                log.debug("WS session close failed. sessionId={}", sessionId, e);
            }
        }
        return closed;
    }

    /** 이 STOMP 세션을 연 사용자. 연결이 인증되지 않았거나 이미 끊겼으면 {@code null}. */
    public UUID userIdOf(String sessionId) {
        return usersBySessionId.get(sessionId);
    }

    public int activeCount() {
        return activeSessionIds.size();
    }

    private String sessionIdOf(Map<String, Object> headers) {
        Object sessionId = headers.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
        return sessionId instanceof String value ? value : null;
    }

    private UUID userIdOf(Principal principal) {
        if (principal instanceof Authentication authentication
            && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }
}
