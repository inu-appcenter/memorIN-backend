package com.memorin.domain.chat_rooms.service;

import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "이 사용자가 이 방의 메시지를 받을 자격이 있는가"를 답하는 단일 창구.
 *
 * <p>STOMP 경로에서 이 질문은 두 번 나온다 — SUBSCRIBE를 받을 때와, 브로커가 메시지를
 * 실제로 내보낼 때다. 두 곳이 각자 판정하면 조건이 갈라지고, 그게 정확히 #141·#210이
 * 생긴 방식이다. 그래서 판정을 여기 하나로 모은다.
 *
 * <p><b>왜 캐시가 필요한가</b> — 구독은 드물지만 배달은 잦다. 방 하나에 8명이 붙어 있고
 * 초당 100건이 오가면 판정이 초당 800번 필요하다. 매번 DB를 치면 HikariCP 커넥션
 * (기본 풀 10개)을 발신 채널 스레드가 계속 빌려가게 되고, 그건 채팅 지연으로 바로 돌아온다.
 * 실측 근거는 {@code docs/ws-stress-test.md}에 있다.
 *
 * <p><b>왜 캐시가 안전한가</b> — 이 판정의 답이 바뀌는 순간은 강퇴·나가기·재입장 세 곳뿐이고,
 * 셋 다 {@link ChatRoomService}를 지난다. 그 세 곳에서 무효화하므로 캐시가 낡을 수 없다.
 * 무효화는 반드시 <b>커밋 이후</b>에 일어나야 한다({@code ChatRoomMembershipChangedListener}) —
 * 커밋 전에 지우면 아직 이전 값이 보이는 DB를 다시 읽어 낡은 값을 그대로 되심는다.
 *
 * <p>캐시 크기는 "실제로 메시지를 주고받은 (사용자, 방) 쌍"만큼이다. 엔트리 하나가 수십 바이트라
 * 온프레미스 단일 인스턴스 기준으로 문제되는 크기가 아니다. 인스턴스를 여러 대로 늘리면
 * 이 캐시는 인스턴스마다 따로 존재하는데, 그건 InMemory 브로커 자체의 제약과 같은 선이라
 * 새로 생기는 제약은 아니다({@code docs/sprint4-architecture-review.md} §3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomMembershipGate {

    private final ChatRoomMemberRepository chatRoomMemberRepository;

    private final Map<String, Boolean> activeMemberCache = new ConcurrentHashMap<>();

    /**
     * 해당 사용자가 그 방의 <b>활성</b> 멤버인가.
     *
     * <p>{@code leftAt IS NULL}이 핵심이다. {@code chat_room_members}는 {@code uq_room_member}
     * 제약 때문에 나갈 때 행을 지우지 않고 {@code leftAt}만 채우므로, 행의 존재만 보면
     * 나간 사람도 강퇴당한 사람도 통과한다.
     */
    public boolean isActiveMember(UUID userId, UUID roomId) {
        String key = key(userId, roomId);

        Boolean cached = activeMemberCache.get(key);
        if (cached != null) {
            return cached;
        }

        // computeIfAbsent를 쓰지 않는다. 매핑 함수 안에서 DB를 치면 그동안 같은 버킷의
        // 다른 키까지 잠긴다. 동시에 두 스레드가 같은 질의를 할 수는 있지만 결과가 같으므로 무해하다.
        boolean active = chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId);
        activeMemberCache.put(key, active);
        return active;
    }

    /**
     * 멤버십이 바뀌었으니 다음 판정은 DB에서 다시 읽으라는 뜻.
     *
     * <p>"거부"를 심는 것이 아니라 "모름"으로 되돌린다. 강퇴·나가기면 다음 조회가 false를,
     * 재입장이면 true를 가져온다. 어느 방향이든 DB가 답을 준다.
     */
    public void invalidate(UUID userId, UUID roomId) {
        activeMemberCache.remove(key(userId, roomId));
        log.debug("멤버십 캐시 무효화. userId={}, roomId={}", userId, roomId);
    }

    private static String key(UUID userId, UUID roomId) {
        return userId + ":" + roomId;
    }
}
