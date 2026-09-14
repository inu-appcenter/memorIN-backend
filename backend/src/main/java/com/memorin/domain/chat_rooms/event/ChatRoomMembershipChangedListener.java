package com.memorin.domain.chat_rooms.event;

import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 멤버십 변경을 멤버십 캐시에 반영한다.
 *
 * <p><b>AFTER_COMMIT인 이유</b> — 커밋 전에 무효화하면 그 직후 도착한 메시지 배달이
 * DB를 다시 읽는데, 그 시점 DB에는 아직 변경이 보이지 않는다. 낡은 값을 캐시에 되심어
 * 무효화가 없던 일이 된다. 커밋 이후에 지워야 다음 조회가 새 값을 본다.
 *
 * <p>비동기로 돌리지 않는다. 강퇴 직후의 짧은 창을 줄이는 것이 이 작업의 목적인데
 * 스레드 풀에 넘기면 그 창이 다시 늘어난다. 하는 일은 맵에서 키 하나 제거뿐이다.
 */
@Component
@RequiredArgsConstructor
public class ChatRoomMembershipChangedListener {

    private final ChatRoomMembershipGate membershipGate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipChanged(ChatRoomMembershipChanged event) {
        membershipGate.invalidate(event.userId(), event.roomId());
    }
}
