package com.memorin.domain.chat_rooms;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.chat_rooms.dto.request.InviteMembersRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomSummaryResponse;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.domain.messages.entity.Messages;
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

// #258: GET /api/chat-rooms의 unreadCount·lastMessage 계산 규칙을 검증한다.
// 데이터를 심는 트랜잭션과 조회 트랜잭션을 분리한다 — ChatRoomListQueryCountTest와 같은 이유로,
// 같은 트랜잭션에서 심고 바로 읽으면 1차 캐시가 native query 결과 확인을 가로챌 수 있다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRoomUnreadCountTest extends PostgresTestSupport {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private User createUser(String tag) {
        return tx.execute(status -> {
            User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
            em.persist(user);
            return user;
        });
    }

    private void sendMessage(UUID roomId, UUID senderId, String text) {
        tx.execute(status -> {
            ChatRooms room = em.getReference(ChatRooms.class, roomId);
            User sender = em.getReference(User.class, senderId);
            em.persist(Messages.createText(room, sender, "{\"type\":\"TEXT\",\"text\":\"%s\"}".formatted(text)));
            return null;
        });
    }

    private ChatRoomSummaryResponse summaryOf(UUID userId, UUID roomId) {
        return chatRoomService.listMyRooms(userId).stream()
            .filter(r -> r.roomId().equals(roomId))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void 메시지가_없으면_안읽음은_0이고_마지막_메시지는_없다() {
        User owner = createUser("empty" + UUID.randomUUID().toString().substring(0, 6));
        UUID roomId = tx.execute(status -> {
            ChatRooms room = ChatRooms.createGroup("빈방");
            em.persist(room);
            em.persist(ChatRoomMembers.ofOwner(room, owner));
            return room.getId();
        });

        ChatRoomSummaryResponse summary = summaryOf(owner.getId(), roomId);

        assertThat(summary.unreadCount()).isZero();
        assertThat(summary.lastMessage()).isNull();
    }

    @Test
    void 상대가_보낸_메시지는_안읽음에_잡히고_내가_보낸_메시지는_잡히지_않는다() {
        String tag = "pair" + UUID.randomUUID().toString().substring(0, 6);
        User me = createUser(tag + "-me");
        User other = createUser(tag + "-other");
        UUID roomId = tx.execute(status -> {
            ChatRooms room = ChatRooms.createGroup("페어방");
            em.persist(room);
            em.persist(ChatRoomMembers.ofOwner(room, me));
            em.persist(ChatRoomMembers.ofMember(room, other));
            return room.getId();
        });

        sendMessage(roomId, other.getId(), "상대 메시지");
        sendMessage(roomId, me.getId(), "내 메시지");

        ChatRoomSummaryResponse summary = summaryOf(me.getId(), roomId);

        assertThat(summary.unreadCount())
            .as("내가 보낸 메시지는 내 안읽음 수에 포함되면 안 된다")
            .isEqualTo(1);
        assertThat(summary.lastMessage().preview()).isEqualTo("내 메시지");
    }

    @Test
    void 읽음_처리_이후_다시_조회하면_안읽음이_0이_된다() {
        String tag = "read" + UUID.randomUUID().toString().substring(0, 6);
        User me = createUser(tag + "-me");
        User other = createUser(tag + "-other");
        UUID roomId = tx.execute(status -> {
            ChatRooms room = ChatRooms.createGroup("읽음방");
            em.persist(room);
            em.persist(ChatRoomMembers.ofOwner(room, me));
            em.persist(ChatRoomMembers.ofMember(room, other));
            return room.getId();
        });

        sendMessage(roomId, other.getId(), "안 읽은 메시지");
        assertThat(summaryOf(me.getId(), roomId).unreadCount()).isEqualTo(1);

        chatRoomService.markAsRead(roomId, me.getId());

        assertThat(summaryOf(me.getId(), roomId).unreadCount()).isZero();
    }

    // rejoin()은 last_read_at을 그대로 두고 joined_at만 갱신한다. 기준 시각을
    // last_read_at 하나만 썼다면 자리를 비운 동안 쌓인 메시지까지 재입장 직후 안읽음으로 잡힌다.
    @Test
    void 재입장하면_비운_동안_쌓인_메시지는_안읽음에서_제외된다() {
        String tag = "rejoin" + UUID.randomUUID().toString().substring(0, 6);
        User owner = createUser(tag + "-owner");
        User member = createUser(tag + "-member");
        UUID roomId = tx.execute(status -> {
            ChatRooms room = ChatRooms.createGroup("재입장방");
            em.persist(room);
            em.persist(ChatRoomMembers.ofOwner(room, owner));
            em.persist(ChatRoomMembers.ofMember(room, member));
            return room.getId();
        });

        chatRoomService.leaveRoom(roomId, member.getId());
        sendMessage(roomId, owner.getId(), "자리 비운 동안 온 메시지");

        chatRoomService.inviteMembers(roomId, owner.getId(), new InviteMembersRequest(List.of(member.getId())));

        assertThat(summaryOf(member.getId(), roomId).unreadCount())
            .as("재입장 이전 메시지는 안읽음에 포함하지 않는다")
            .isZero();

        sendMessage(roomId, owner.getId(), "재입장 이후 메시지");

        assertThat(summaryOf(member.getId(), roomId).unreadCount()).isEqualTo(1);
    }
}
