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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Tracks authenticated STOMP sessions so push is sent only to offline users. */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, UUID> usersBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<String>> sessionIdsByUserId = new ConcurrentHashMap<>();
    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();

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

    public boolean isConnected(UUID userId) {
        Set<String> sessionIds = sessionIdsByUserId.get(userId);
        return sessionIds != null && !sessionIds.isEmpty();
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
