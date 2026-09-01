package com.memorin.domain.chat_rooms;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.chat_room_members.entity.Members_role;
import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.dto.request.InviteMembersRequest;
import com.memorin.domain.chat_rooms.dto.request.RenameRoomRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.repository.ChatRoomsRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 채팅방 생성/관리 API의 "잘못되면 바로 체감되는" 경로를 검증한다.
//
// 특히 아래 두 개는 실제로 있었던 버그를 회귀 방지하기 위한 테스트다.
//  1. ChatRoomMembers.of()가 UUID를 참조 비교(!=)해서, 방을 만든 사람이 자기 방에서
//     OWNER가 안 되던 버그가 있었다 — ofOwner()/ofMember()가 실제로 올바른 role을
//     배정하는지 반드시 확인해야 한다.
//  2. chat_room_members에는 (room_id, user_id) 유니크 제약이 있다. 나갔던 사람을
//     다시 초대할 때 새 행을 INSERT하면 그 제약을 위반해 500이 난다 — rejoin 경로가
//     실제로 그 제약을 피해가는지 확인해야 한다.
//
// 여기서 쓰는 User는 매번 랜덤 태그로 새로 만들기 때문에, 다른 테스트/이전 실행이
// 남긴 데이터와 섞일 일이 없다 (게시물 검색 테스트와 달리 특정 사용자 ID로만 조회하므로).
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRoomCriticalPathTest extends PostgresTestSupport {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatRoomsRepository chatRoomsRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private User persistUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        em.persist(user);
        return user;
    }

    @Test
    void 이미_1대1_방이_있으면_새로_만들지_않고_기존_방을_반환한다() {
        UUID[] ids = tx.execute(status -> {
            User a = persistUser("a" + UUID.randomUUID().toString().substring(0, 6));
            User b = persistUser("b" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return new UUID[]{a.getId(), b.getId()};
        });

        ChatRoomResponse first = chatRoomService.createDirectRoom(ids[0], ids[1]);
        ChatRoomResponse second = chatRoomService.createDirectRoom(ids[0], ids[1]);
        // 요청 방향이 바뀌어도(상대가 나에게 먼저 걸어도) 같은 방으로 잡혀야 한다.
        ChatRoomResponse reversed = chatRoomService.createDirectRoom(ids[1], ids[0]);

        assertThat(first.newlyCreated()).isTrue();
        assertThat(second.newlyCreated()).isFalse();
        assertThat(reversed.newlyCreated()).isFalse();
        assertThat(second.roomId()).isEqualTo(first.roomId());
        assertThat(reversed.roomId()).isEqualTo(first.roomId());

        assertThat(chatRoomMemberRepository.findByUser_IdAndLeftAtIsNullOrderByJoinedAtDesc(ids[0]))
            .as("같은 상대와의 1:1 방은 하나만 존재해야 한다")
            .hasSize(1);
    }

    @Test
    void 자기_자신과는_1대1_채팅방을_만들_수_없다() {
        UUID userId = tx.execute(status -> persistUser("solo" + UUID.randomUUID().toString().substring(0, 6)).getId());

        assertThatThrownBy(() -> chatRoomService.createDirectRoom(userId, userId))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 방을_만든_사람은_실제로_OWNER_역할을_갖는다() {
        UUID[] ids = tx.execute(status -> {
            User a = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User b = persistUser("member" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return new UUID[]{a.getId(), b.getId()};
        });

        ChatRoomResponse room = chatRoomService.createGroupRoom(ids[0],
            new CreateGroupRoomRequest("테스트방", List.of(ids[1])));

        ChatRoomMembers ownerMembership = chatRoomMemberRepository
            .findByRoom_IdAndUser_Id(room.roomId(), ids[0]).orElseThrow();
        ChatRoomMembers memberMembership = chatRoomMemberRepository
            .findByRoom_IdAndUser_Id(room.roomId(), ids[1]).orElseThrow();

        assertThat(ownerMembership.getRole())
            .as("방 생성자는 OWNER여야 한다 (UUID 참조 비교 버그 회귀 방지)")
            .isEqualTo(Members_role.OWNER);
        assertThat(memberMembership.getRole()).isEqualTo(Members_role.MEMBER);
    }

    @Test
    void OWNER가_아니면_강퇴할_수_없다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User member = persistUser("member" + UUID.randomUUID().toString().substring(0, 6));
            User target = persistUser("target" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return new UUID[]{owner.getId(), member.getId(), target.getId()};
        });

        ChatRoomResponse room = chatRoomService.createGroupRoom(ids[0],
            new CreateGroupRoomRequest("테스트방", List.of(ids[1], ids[2])));

        assertThatThrownBy(() -> chatRoomService.kickMember(room.roomId(), ids[1], ids[2]))
            .as("일반 멤버가 다른 멤버를 강퇴하면 안 된다")
            .isInstanceOf(com.memorin.global.exception.BusinessException.class);
    }

    @Test
    void 자기_자신은_강퇴할_수_없다() {
        UUID ownerId = tx.execute(status ->
            persistUser("owner" + UUID.randomUUID().toString().substring(0, 6)).getId());

        ChatRoomResponse room = chatRoomService.createGroupRoom(ownerId,
            new CreateGroupRoomRequest("테스트방", List.of()));

        assertThatThrownBy(() -> chatRoomService.kickMember(room.roomId(), ownerId, ownerId))
            .isInstanceOf(com.memorin.global.exception.BusinessException.class);
    }

    @Test
    void 나갔던_멤버를_다시_초대해도_유니크_제약_위반_없이_재가입된다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User member = persistUser("member" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return new UUID[]{owner.getId(), member.getId()};
        });

        ChatRoomResponse room = chatRoomService.createGroupRoom(ids[0],
            new CreateGroupRoomRequest("테스트방", List.of(ids[1])));

        chatRoomService.leaveRoom(room.roomId(), ids[1]);
        // (room_id, user_id) 유니크 제약 때문에, 재초대가 새 행을 INSERT하는 방식이면
        // 여기서 DataIntegrityViolationException이 터진다.
        chatRoomService.inviteMembers(room.roomId(), ids[0], new InviteMembersRequest(List.of(ids[1])));

        ChatRoomMembers membership = chatRoomMemberRepository
            .findByRoom_IdAndUser_Id(room.roomId(), ids[1]).orElseThrow();

        assertThat(membership.isActive()).as("재초대 후에는 다시 활성 멤버여야 한다").isTrue();
        assertThat(chatRoomMemberRepository.findAll().stream()
            .filter(m -> m.getRoom().getId().equals(room.roomId()) && m.getUser().getId().equals(ids[1])))
            .as("같은 (room, user) 조합으로 행이 두 개 생기면 안 된다 — 반드시 기존 행을 재사용해야 한다")
            .hasSize(1);
    }

    @Test
    void OWNER가_나가면_남은_멤버_중_한_명에게_방장이_위임된다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User member = persistUser("member" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return new UUID[]{owner.getId(), member.getId()};
        });

        ChatRoomResponse room = chatRoomService.createGroupRoom(ids[0],
            new CreateGroupRoomRequest("테스트방", List.of(ids[1])));

        chatRoomService.leaveRoom(room.roomId(), ids[0]);

        ChatRoomMembers remaining = chatRoomMemberRepository
            .findByRoom_IdAndUser_Id(room.roomId(), ids[1]).orElseThrow();

        assertThat(remaining.getRole())
            .as("OWNER가 나가면 방장 없는 그룹방이 남으면 안 된다")
            .isEqualTo(Members_role.OWNER);
    }

    @Test
    void 일대일_방에는_이름_변경이나_초대_같은_그룹_전용_기능을_쓸_수_없다() {
        UUID[] ids = tx.execute(status -> {
            User a = persistUser("a" + UUID.randomUUID().toString().substring(0, 6));
            User b = persistUser("b" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return new UUID[]{a.getId(), b.getId()};
        });

        ChatRoomResponse room = chatRoomService.createDirectRoom(ids[0], ids[1]);

        assertThatThrownBy(() ->
            chatRoomService.renameRoom(room.roomId(), ids[0], new RenameRoomRequest("이름")))
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() ->
            chatRoomService.inviteMembers(room.roomId(), ids[0], new InviteMembersRequest(List.of(ids[1]))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 나간_멤버는_방_관련_작업을_수행할_수_없다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User member = persistUser("member" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return new UUID[]{owner.getId(), member.getId()};
        });

        ChatRoomResponse room = chatRoomService.createGroupRoom(ids[0],
            new CreateGroupRoomRequest("테스트방", List.of(ids[1])));

        chatRoomService.leaveRoom(room.roomId(), ids[1]);

        assertThatThrownBy(() -> chatRoomService.leaveRoom(room.roomId(), ids[1]))
            .as("이미 나간 사람이 다시 나가기를 시도하면 활성 멤버가 아니므로 거부돼야 한다")
            .isInstanceOf(com.memorin.global.exception.BusinessException.class);
    }
}
