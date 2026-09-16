package com.memorin.domain.emoji;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_rooms.entity.Chat_type;
import com.memorin.domain.emoji.dto.response.EmojiSummary;
import com.memorin.domain.emoji.dto.response.EmojiToggleResponse;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.emoji.repository.MessageEmojiRepository;
import com.memorin.domain.emoji.service.MessageEmojiService;
import com.memorin.domain.messages.entity.Messages;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.global.exception.BusinessException;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 메시지 이모지 토글/삭제/집계 API의 핵심 경로를 검증한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MessageEmojiPersistenceTest extends PostgresTestSupport {

    @Autowired
    private MessageEmojiService messageEmojiService;

    @Autowired
    private MessageEmojiRepository messageEmojiRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private static final EmojiType TYPE_1 = EmojiType.values()[0];
    private static final EmojiType TYPE_2 = EmojiType.values()[1];

    private User persistUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        em.persist(user);
        return user;
    }

    private Post persistPost(User owner) {
        Post post = Post.create(owner,
            "[{\"type\":\"text\",\"text\":\"방 생성용 더미 게시물\"}]",
            VisibilityType.PUBLIC, TimeslotType.AM, Date.valueOf(LocalDate.of(2026, 8, 1)), List.of());
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

    private Messages persistMessage(ChatRooms room, User sender) {
        Messages message = Messages.createText(room, sender, "{\"type\":\"TEXT\",\"text\":\"hi\"}");
        em.persist(message);
        return message;
    }

    @Test
    void 이모지를_처음_누르면_추가되고_DB에_저장된다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("room", post, owner);
            Messages message = persistMessage(room, owner);
            em.flush();
            return new UUID[]{owner.getId(), message.getId()};
        });
        UUID userId = ids[0];
        UUID messageId = ids[1];

        EmojiToggleResponse response = messageEmojiService.toggle(userId, messageId, TYPE_1);

        assertThat(response.added()).isTrue();
        assertThat(messageEmojiRepository.findByUserIdAndMessageIdAndEmojiType(userId, messageId, TYPE_1))
            .as("토글 결과가 실제로 DB에 반영돼야 한다")
            .isPresent();
    }

    @Test
    void 같은_이모지를_다시_누르면_취소된다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("room", post, owner);
            Messages message = persistMessage(room, owner);
            em.flush();
            return new UUID[]{owner.getId(), message.getId()};
        });
        UUID userId = ids[0];
        UUID messageId = ids[1];

        messageEmojiService.toggle(userId, messageId, TYPE_1);
        EmojiToggleResponse second = messageEmojiService.toggle(userId, messageId, TYPE_1);

        assertThat(second.added())
            .as("같은 이모지를 두 번째 누르면 취소(added=false)돼야 한다")
            .isFalse();
        assertThat(messageEmojiRepository.findByUserIdAndMessageIdAndEmojiType(userId, messageId, TYPE_1))
            .isEmpty();
    }

    @Test
    void 삭제된_메시지에는_이모지를_달_수_없다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("room", post, owner);
            Messages message = persistMessage(room, owner);
            message.softDelete();
            em.flush();
            return new UUID[]{owner.getId(), message.getId()};
        });
        UUID userId = ids[0];
        UUID messageId = ids[1];

        assertThatThrownBy(() -> messageEmojiService.toggle(userId, messageId, TYPE_1))
            .as("삭제된(tombstone) 메시지에는 이모지를 달 수 없어야 한다")
            .isInstanceOf(BusinessException.class);

        assertThat(messageEmojiRepository.findByUserIdAndMessageIdAndEmojiType(userId, messageId, TYPE_1))
            .as("예외가 나면 실제로 저장되면 안 된다")
            .isEmpty();
    }

    @Test
    void 존재하지_않는_메시지에는_이모지를_달_수_없다() {
        UUID userId = tx.execute(status ->
            persistUser("solo" + UUID.randomUUID().toString().substring(0, 6)).getId());

        assertThatThrownBy(() -> messageEmojiService.toggle(userId, UUID.randomUUID(), TYPE_1))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void 한_사용자가_같은_메시지에_서로_다른_이모지를_동시에_달_수_있다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("room", post, owner);
            Messages message = persistMessage(room, owner);
            em.flush();
            return new UUID[]{owner.getId(), message.getId()};
        });
        UUID userId = ids[0];
        UUID messageId = ids[1];

        messageEmojiService.toggle(userId, messageId, TYPE_1);
        messageEmojiService.toggle(userId, messageId, TYPE_2);

        assertThat(messageEmojiRepository.findByUserIdAndMessageIdAndEmojiType(userId, messageId, TYPE_1)).isPresent();
        assertThat(messageEmojiRepository.findByUserIdAndMessageIdAndEmojiType(userId, messageId, TYPE_2))
            .as("유니크 제약이 (user, message, emojiType) 조합이라 이모지 종류가 다르면 같이 달릴 수 있어야 한다")
            .isPresent();
    }

    @Test
    void remove는_달지_않은_이모지를_지워도_예외_없이_멱등하게_처리된다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("room", post, owner);
            Messages message = persistMessage(room, owner);
            em.flush();
            return new UUID[]{owner.getId(), message.getId()};
        });
        UUID userId = ids[0];
        UUID messageId = ids[1];

        // 이 유저는 이 메시지에 TYPE_1을 단 적이 없다.
        messageEmojiService.remove(userId, messageId, TYPE_1);

        // 존재하지 않는 messageId에 대해서도 예외 없이 조용히 넘어가야 한다.
        messageEmojiService.remove(userId, UUID.randomUUID(), TYPE_1);
    }

    @Test
    void 집계_조회는_개수와_본인_반응_여부를_정확히_반환한다() {
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User other = persistUser("other" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner);
            ChatRooms room = persistRoom("room", post, owner, other);
            Messages message = persistMessage(room, owner);
            em.flush();
            return new UUID[]{owner.getId(), other.getId(), message.getId()};
        });
        UUID ownerId = ids[0];
        UUID otherId = ids[1];
        UUID messageId = ids[2];

        messageEmojiService.toggle(ownerId, messageId, TYPE_1);
        messageEmojiService.toggle(otherId, messageId, TYPE_1);

        EmojiSummary ownerView = messageEmojiService.getEmojis(messageId, ownerId).stream()
            .filter(s -> s.emojiType() == TYPE_1)
            .findFirst()
            .orElseThrow();

        assertThat(ownerView.count()).isEqualTo(2);
        assertThat(ownerView.reactedByMe())
            .as("owner 본인도 반응했으니 reactedByMe는 true여야 한다")
            .isTrue();

        EmojiSummary strangerView = messageEmojiService.getEmojis(messageId, UUID.randomUUID()).stream()
            .filter(s -> s.emojiType() == TYPE_1)
            .findFirst()
            .orElseThrow();

        assertThat(strangerView.count()).isEqualTo(2);
        assertThat(strangerView.reactedByMe())
            .as("반응한 적 없는 사람에게는 reactedByMe가 false여야 한다")
            .isFalse();
    }
}
