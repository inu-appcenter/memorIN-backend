package com.memorin.global.config;

import com.memorin.domain.auth.event.UserSessionsRevoked;
import com.memorin.domain.chat_rooms.event.ChatRoomMembershipChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 강퇴·탈퇴·로그아웃 뒤에 그 사용자의 열린 웹소켓 세션을 끊는다(#304).
 *
 * <p>배달 필터({@link ChatDeliveryChannelInterceptor})가 메시지는 막지만, 연결 자체는 남는다.
 * 로그아웃·탈퇴한 기기의 연결이 살아 있으면 다른 방 메시지를 계속 받고, 강퇴당한 사람의 클라이언트는
 * 강퇴됐다는 사실을 알 길이 없다.
 *
 * <p><b>AFTER_COMMIT</b> — 롤백된 요청으로 연결을 끊지 않기 위해서다. 재연결은 커밋된 상태로 다시
 * 인증·인가를 거친다. 트랜잭션 밖에서 발행돼도 실행되도록 {@code fallbackExecution}을 켠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionTerminationListener {

    private final WebSocketSessionRegistry sessionRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserSessionsRevoked(UserSessionsRevoked event) {
        int closed = sessionRegistry.closeSessionsOf(event.userId());
        if (closed > 0) {
            log.info("Closed WS sessions on revoke. userId={}, closed={}", event.userId(), closed);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMembershipChanged(ChatRoomMembershipChanged event) {
        if (event.reason() != ChatRoomMembershipChanged.Reason.KICKED) {
            return;
        }
        int closed = sessionRegistry.closeSessionsSubscribedTo(event.userId(), event.roomId());
        if (closed > 0) {
            log.info("Closed WS sessions on kick. userId={}, roomId={}, closed={}",
                event.userId(), event.roomId(), closed);
        }
    }
}
