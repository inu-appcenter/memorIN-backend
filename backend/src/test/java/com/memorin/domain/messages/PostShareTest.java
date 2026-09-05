package com.memorin.domain.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_rooms.entity.Chat_type;
import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.messages.dto.request.PostShareRequest;
import com.memorin.domain.messages.entity.MessageType;
import com.memorin.domain.messages.entity.Messages;
import com.memorin.domain.messages.repository.MessageRepository;
import com.memorin.domain.messages.service.MessageService;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import com.memorin.domain.posts.entity.TagType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import com.memorin.global.exception.PostExceptions;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


// 게시물 공유 API(MessageService#sharePost)의 "실패해야만 하는" 경로를 검증한다.
//
// 이 API가 위험한 이유는 두 가지다.
//  1. 다이어리 특성상 비공개 글이 있는데, 검증 순서가 바뀌면
//     "권한 없음" 예외를 던지기 *전에* 메시지가 먼저 저장돼버릴 수 있다.
//     (검증 로직이 저장 로직 뒤로 옮겨지는 리팩터링 한 번이면 조용히 발생한다.)
//  2. 공유 메시지에는 postId만 저장하기로 설계했다 — "카드 전송"이 아니라 "이동 링크"로 바꾼 이유가
//     바로 이거다. 누군가 미리보기를 예쁘게 만들려고 게시물 본문을 content에 같이 넣기 시작하면,
//     이후 게시물이 비공개로 바뀌거나 삭제돼도 채팅 내역에는 원문이 영구히 남는 privacy leak이 된다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostShareTest extends PostgresTestSupport {

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

    private Post persistPost(User owner, VisibilityType visibility) {
        Post post = Post.create(owner,
            "[{\"type\":\"text\",\"text\":\"매우 사적인 일기 내용\"}]",
            visibility, TimeslotType.AM, Date.valueOf(LocalDate.of(2026, 8, 1)), List.of(TagType.ETC));
        em.persist(post);
        return post;
    }

    @Test
    void 비공개_게시물은_소유자가_아니면_공유할_수_없고_메시지도_저장되지_않는다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User stranger = persistUser("stranger" + UUID.randomUUID().toString().substring(0, 6));
            Post privatePost = persistPost(owner, VisibilityType.PRIVATE); // 실제 비공개 값 이름으로 조정
            ChatRooms room = persistRoom("test-room", privatePost, owner, stranger);
            em.flush();
            return new UUID[]{stranger.getId(), room.getId(), privatePost.getId()};
        });
        long beforeCount = messagesRepository.count();

        assertThatThrownBy(() ->
            messageService.sharePost(ids[0], new PostShareRequest(ids[1], ids[2])))
            .as("방 참여자여도 비공개 게시물 소유자가 아니면 막혀야 한다")
            .isInstanceOf(PostExceptions.PostAccessDeniedException.class);

        // 권한 실패인데 메시지 행이 하나라도 늘었다면, 검증보다 저장이 먼저 실행된 것이다.
        assertThat(messagesRepository.count())
            .as("권한 검증에 실패하면 메시지가 절대 저장되면 안 된다")
            .isEqualTo(beforeCount);
    }

    @Test
    void 채팅방_참여자가_아니면_공유할_수_없고_메시지도_저장되지_않는다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User outsider = persistUser("outsider" + UUID.randomUUID().toString().substring(0, 6));
            Post publicPost = persistPost(owner, VisibilityType.PUBLIC);
            ChatRooms room = persistRoom("members-only-room", publicPost, owner); // outsider는 넣지 않음
            em.flush();
            return new UUID[]{outsider.getId(), room.getId(), publicPost.getId()};
        });
        long beforeCount = messagesRepository.count();

        assertThatThrownBy(() ->
            messageService.sharePost(ids[0], new PostShareRequest(ids[1], ids[2])))
            .as("공개 게시물이라도 방 참여자가 아니면 그 방으로는 공유할 수 없다")
            .isInstanceOf(com.memorin.global.exception.BusinessException.class);

        assertThat(messagesRepository.count()).isEqualTo(beforeCount);
    }

    // 소프트 삭제는 deletedAt만 채우고 행은 남긴다. "존재 여부"만 확인하고 "삭제 여부"를
    // 빼먹으면 삭제된 글이 버젓이 공유되는 버그가 조용히 생긴다. 소유자 본인이 시도해도
    // 막혀야 한다는 게 핵심 — 소유자 체크가 삭제 체크보다 먼저 통과하면 안 된다.
    @Test
    void 삭제된_게시물은_소유자여도_공유할_수_없다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner, VisibilityType.PUBLIC);
            post.softDelete();
            ChatRooms room = persistRoom("room", post, owner);
            em.flush();
            return new UUID[]{owner.getId(), room.getId(), post.getId()};
        });

        assertThatThrownBy(() ->
            messageService.sharePost(ids[0], new PostShareRequest(ids[1], ids[2])))
            .isInstanceOf(com.memorin.global.exception.BusinessException.class);
    }

    @Test
    void 공유_메시지_content에는_postId만_들어가고_게시물_본문은_들어가지_않는다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner, VisibilityType.PUBLIC); // content에 "매우 사적인 일기 내용" 포함
            ChatRooms room = persistRoom("room", post, owner);
            em.flush();
            return new UUID[]{owner.getId(), room.getId(), post.getId()};
        });

        messageService.sharePost(ids[0], new PostShareRequest(ids[1], ids[2]));

        Messages saved = messagesRepository.findAll().stream()
            .filter(m -> m.getType() == MessageType.POST_SHARE)
            .findFirst()
            .orElseThrow();

        assertThat(saved.getContent())
            .as("게시물 본문 텍스트가 그대로 새어 들어가면 안 된다")
            .doesNotContain("매우 사적인 일기 내용");

        JsonNode json = readTree(saved.getContent());
        assertThat(json.get("type").asText()).isEqualTo("POST_SHARE");
        assertThat(UUID.fromString(json.get("postId").asText())).isEqualTo(ids[2]);
        assertThat(json.has("snapshot")).as("스냅샷 필드가 부활하면 안 된다").isFalse();
    }

    @Test
    void 존재하지_않는_게시물이나_채팅방은_NotFound로_처리되고_500으로_새지_않는다() {
        UUID senderId = tx.execute(status ->
            persistUser("solo" + UUID.randomUUID().toString().substring(0, 6)).getId());

        assertThatThrownBy(() ->
            messageService.sharePost(senderId, new PostShareRequest(UUID.randomUUID(), UUID.randomUUID())))
            .isInstanceOf(com.memorin.global.exception.BusinessException.class);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
