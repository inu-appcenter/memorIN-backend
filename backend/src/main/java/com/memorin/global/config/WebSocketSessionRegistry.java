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
