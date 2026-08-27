package com.memorin.domain.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_rooms.entity.Chat_type;
import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.messages.dto.request.TextRequest;
import com.memorin.domain.messages.entity.MessageType;
import com.memorin.domain.messages.entity.Messages;
import com.memorin.domain.messages.repository.MessageRepository;
import com.memorin.domain.messages.service.MessageService;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import com.memorin.global.exception.BusinessException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 텍스트 채팅 메시지 API(MessageService#sendText)의 발신/브로커 경유 이전 단계인
// 서비스 계층 저장 로직을 검증한다. sharePost와 달리 게시물 권한 체크가 없으므로
// "채팅방 참여자 검증"이 유일한 관문이며, 이게 빠지면 방과 무관한 사용자가
// 메시지를 저장시킬 수 있다는 게 핵심 위험이다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TextMessageTest extends PostgresTestSupport {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageRepository messagesRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User persistUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        em.persist(user);
        return user;
    }

    private Post persistPost(User owner) {
        Post post = Post.create(owner,
            "[{\"type\":\"text\",\"text\":\"방 생성용 더미 게시물\"}]",
            VisibilityType.PUBLIC, TimeslotType.AM, Date.valueOf(LocalDate.of(2026, 8, 1)));
        em.persist(post);
        return post;
    }

    private ChatRooms persistRoom(String name, Post post, User... members) {
        ChatRooms room = ChatRooms.builder()
            .name(name)
            .type(Chat_type.GROUP)
            .build();
        em.persist(room);
        for (User member : members) {
            em.persist(ChatRoomMembers.of(room, post, member));
        }
        return room;
    }

    @Test
    void 채팅방_참여자가_아니면_텍스트를_보낼_수_없고_메시지도_저장되지_않는다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User outsider = persistUser("outsider" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("members-only-room", post, owner); // outsider는 넣지 않음
            em.flush();
            return new UUID[]{outsider.getId(), room.getId()};
        });
        long beforeCount = messagesRepository.count();

        assertThatThrownBy(() ->
            messageService.sendText(ids[0], new TextRequest(ids[1], "안녕하세요")))
            .as("방 참여자가 아니면 텍스트 메시지를 보낼 수 없다")
            .isInstanceOf(BusinessException.class);

        assertThat(messagesRepository.count())
            .as("참여자 검증에 실패하면 메시지가 절대 저장되면 안 된다")
            .isEqualTo(beforeCount);
    }

    @Test
    void 존재하지_않는_채팅방으로는_텍스트를_보낼_수_없다() {
        UUID senderId = tx.execute(status ->
            persistUser("solo" + UUID.randomUUID().toString().substring(0, 6)).getId());

        assertThatThrownBy(() ->
            messageService.sendText(senderId, new TextRequest(UUID.randomUUID(), "안녕하세요")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void 텍스트_메시지가_정상적으로_저장되고_content에_text가_들어간다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("room", post, owner);
            em.flush();
            return new UUID[]{owner.getId(), room.getId()};
        });

        messageService.sendText(ids[0], new TextRequest(ids[1], "안녕하세요"));

        Messages saved = messagesRepository.findAll().stream()
            .filter(m -> m.getType() == MessageType.TEXT)
            .findFirst()
            .orElseThrow();

        JsonNode json = readTree(saved.getContent());
        assertThat(json.get("type").asText()).isEqualTo("TEXT");
        assertThat(json.get("text").asText()).isEqualTo("안녕하세요");
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
