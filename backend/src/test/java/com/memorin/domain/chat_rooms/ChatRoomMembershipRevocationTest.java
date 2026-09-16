package com.memorin.domain.chat_rooms;

import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.dto.request.InviteMembersRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.service.ChatRoomMembershipGate;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 강퇴·나가기·재입장이 실제로 멤버십 캐시까지 전파되는지 본다 (#210).
//
// 단위 테스트(ChatRoomMembershipGateTest)는 게이트가 무효화에 반응하는 것까지만 확인한다.
// 여기서 보는 것은 그 앞 단계다 — 서비스가 이벤트를 쏘고, 리스너가 커밋 이후에 받아서
// 무효화까지 이어지는가.
//
// 이 연결이 끊기면 증상이 조용하다. 테스트도 통과하고 에러도 없는데, 강퇴당한 사람에게만
// 대화가 계속 흘러간다. 그래서 실제 트랜잭션을 태워서 확인한다.
//
// 특히 커밋 시점이 중요하다. 커밋 전에 무효화하면 그 직후 조회가 아직 변경이 보이지 않는
// DB를 읽어 낡은 값을 되심는다. 캐시를 미리 데워 두는 것은 그 상황을 만들기 위해서다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRoomMembershipRevocationTest extends PostgresTestSupport {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatRoomMembershipGate membershipGate;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private UUID[] persistOwnerAndMember(String tag) {
        return tx.execute(status -> {
            User owner = new User(tag + "-o@memorin.test", "hash", tag + "-o", tag + "-o", null);
            User member = new User(tag + "-m@memorin.test", "hash", tag + "-m", tag + "-m", null);
            em.persist(owner);
            em.persist(member);
            em.flush();
            return new UUID[]{owner.getId(), member.getId()};
        });
    }

    private static String tag(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void 강퇴하면_캐시된_수신_자격이_즉시_사라진다() {
        UUID[] ids = persistOwnerAndMember(tag("kick"));
        ChatRoomResponse room = chatRoomService.createGroupRoom(
            ids[0], new CreateGroupRoomRequest("강퇴 방", List.of(ids[1])));

        // 캐시를 데운다. 실제로는 메시지가 한 번이라도 배달되면 이 상태가 된다.
        assertThat(membershipGate.isActiveMember(ids[1], room.roomId())).isTrue();

        chatRoomService.kickMember(room.roomId(), ids[0], ids[1]);

        assertThat(membershipGate.isActiveMember(ids[1], room.roomId()))
            .as("강퇴 후에는 이미 맺은 구독으로도 메시지가 가면 안 된다")
            .isFalse();
    }

    @Test
    void 방을_나가면_캐시된_수신_자격이_즉시_사라진다() {
        UUID[] ids = persistOwnerAndMember(tag("leave"));
        ChatRoomResponse room = chatRoomService.createGroupRoom(
            ids[0], new CreateGroupRoomRequest("나가기 방", List.of(ids[1])));

        assertThat(membershipGate.isActiveMember(ids[1], room.roomId())).isTrue();

        chatRoomService.leaveRoom(room.roomId(), ids[1]);

        assertThat(membershipGate.isActiveMember(ids[1], room.roomId())).isFalse();
    }

    // 반대 방향. 무효화가 "거부를 심는 것"이면 재입장한 사람이 영영 메시지를 못 받는다.
    @Test
    void 다시_초대하면_수신_자격이_되살아난다() {
        UUID[] ids = persistOwnerAndMember(tag("rejoin"));
        ChatRoomResponse room = chatRoomService.createGroupRoom(
            ids[0], new CreateGroupRoomRequest("재입장 방", List.of(ids[1])));

        chatRoomService.kickMember(room.roomId(), ids[0], ids[1]);
        assertThat(membershipGate.isActiveMember(ids[1], room.roomId())).isFalse();

        chatRoomService.inviteMembers(room.roomId(), ids[0], new InviteMembersRequest(List.of(ids[1])));

        assertThat(membershipGate.isActiveMember(ids[1], room.roomId()))
            .as("재입장하면 다시 받을 수 있어야 한다")
            .isTrue();
    }

    // 한 방에서 강퇴당했다고 다른 방까지 막히면 안 된다.
    @Test
    void 한_방에서_강퇴당해도_다른_방_수신은_유지된다() {
        UUID[] ids = persistOwnerAndMember(tag("two"));
        ChatRoomResponse kicked = chatRoomService.createGroupRoom(
            ids[0], new CreateGroupRoomRequest("강퇴될 방", List.of(ids[1])));
        ChatRoomResponse kept = chatRoomService.createGroupRoom(
            ids[0], new CreateGroupRoomRequest("남아 있을 방", List.of(ids[1])));

        assertThat(membershipGate.isActiveMember(ids[1], kept.roomId())).isTrue();

        chatRoomService.kickMember(kicked.roomId(), ids[0], ids[1]);

        assertThat(membershipGate.isActiveMember(ids[1], kicked.roomId())).isFalse();
        assertThat(membershipGate.isActiveMember(ids[1], kept.roomId()))
            .as("강퇴는 그 방에만 적용돼야 한다")
            .isTrue();
    }
}
