package com.memorin.domain.chat_rooms;

import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 멤버십 판정 캐시.
//
// 캐시는 편의가 아니라 필요다. 방 하나에 8명이 붙어 초당 100건이 오가면 판정이 초당 800번
// 필요한데, 매번 DB를 치면 발신 채널 스레드가 HikariCP 커넥션(기본 10개)을 계속 빌려간다.
//
// 다만 캐시는 낡으면 그 자체가 결함이다. 강퇴했는데 계속 받거나, 재입장했는데 못 받는다.
// 그래서 이 파일은 두 가지를 본다 — 캐시가 실제로 DB 조회를 줄이는가, 그리고 무효화가
// 양방향으로 동작하는가.
class ChatRoomMembershipGateTest {

    private ChatRoomMemberRepository repository;
    private ChatRoomMembershipGate gate;

    private UUID userId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        repository = mock(ChatRoomMemberRepository.class);
        gate = new ChatRoomMembershipGate(repository);
        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();
    }

    @Test
    void 같은_질문을_반복해도_DB는_한_번만_읽는다() {
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)).willReturn(true);

        for (int i = 0; i < 50; i++) {
            assertThat(gate.isActiveMember(userId, roomId)).isTrue();
        }

        verify(repository, times(1)).existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId);
    }

    // 강퇴·나가기 방향. 무효화하지 않으면 캐시된 true가 남아 계속 받는다 — #210 그 자체다.
    @Test
    void 무효화하면_강퇴_사실이_다음_판정에_반영된다() {
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)).willReturn(true);
        assertThat(gate.isActiveMember(userId, roomId)).isTrue();

        // 강퇴로 left_at이 찍힌 상태
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)).willReturn(false);

        assertThat(gate.isActiveMember(userId, roomId))
            .as("무효화 전에는 캐시된 값이 그대로 나온다")
            .isTrue();

        gate.invalidate(userId, roomId);

        assertThat(gate.isActiveMember(userId, roomId))
            .as("무효화 후에는 DB를 다시 읽어야 한다")
            .isFalse();
    }

    // 재입장 방향. 무효화가 "거부를 심는 것"이라면 이 테스트가 깨진다.
    // 무효화는 "모름으로 되돌리는 것"이어야 한다.
    @Test
    void 무효화하면_재입장_사실도_다음_판정에_반영된다() {
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)).willReturn(false);
        assertThat(gate.isActiveMember(userId, roomId)).isFalse();

        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)).willReturn(true);
        gate.invalidate(userId, roomId);

        assertThat(gate.isActiveMember(userId, roomId)).isTrue();
    }

    // 캐시 키가 방을 구분하지 않으면 한 방에서 강퇴당했을 때 다른 방까지 막힌다.
    @Test
    void 방마다_판정이_따로_캐시된다() {
        UUID otherRoomId = UUID.randomUUID();
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)).willReturn(false);
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(otherRoomId, userId)).willReturn(true);

        assertThat(gate.isActiveMember(userId, roomId)).isFalse();
        assertThat(gate.isActiveMember(userId, otherRoomId)).isTrue();
    }

    // 사용자를 구분하지 않으면 한 명의 판정이 다른 사람에게 새어나간다.
    @Test
    void 사용자마다_판정이_따로_캐시된다() {
        UUID otherUserId = UUID.randomUUID();
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)).willReturn(true);
        given(repository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, otherUserId)).willReturn(false);

        assertThat(gate.isActiveMember(userId, roomId)).isTrue();
        assertThat(gate.isActiveMember(otherUserId, roomId)).isFalse();
    }
}
