package com.memorin.domain.chat_rooms.event;

import java.util.UUID;

/**
 * 방의 멤버십이 바뀌었다(강퇴·나가기·재입장).
 *
 * <p>이 이벤트를 듣는 쪽은 커밋 이후에만 반응해야 한다. 커밋 전에 캐시를 지우면
 * 아직 이전 값이 보이는 DB를 다시 읽어 낡은 값을 그대로 되심는다.
 * 알림 파이프라인({@code PushNotificationRequested})과 같은 이유·같은 방식이다.
 */
public record ChatRoomMembershipChanged(UUID userId, UUID roomId) {
}
