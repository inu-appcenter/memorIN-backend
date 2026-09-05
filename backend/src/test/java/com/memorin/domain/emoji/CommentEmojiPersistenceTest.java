package com.memorin.domain.emoji;

import com.memorin.domain.emoji.dto.response.EmojiCountDto;
import com.memorin.domain.emoji.entity.CommentEmoji;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.emoji.repository.CommentEmojiRepository;
import com.memorin.domain.post_comments.entity.PostComments;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 댓글 이모지가 운영 스키마(Flyway 마이그레이션)에 실제로 저장·조회되는지 검증한다.
//
// 이모지 API는 머지 후 한동안 두 결함이 겹쳐 전혀 동작하지 않았다(둘 다 실측으로 확인).
//  1. 테이블명 — 엔티티는 comment_emoji인데 DDL은 emoji였다.
//     -> relation "comment_emoji" does not exist
//  2. enum 매핑 — emoji_type은 Postgres 네이티브 ENUM인데 @JdbcTypeCode(NAMED_ENUM)이 없었다.
//     -> column "emoji_type" is of type emoji_type but expression is of type character varying
//
// 1번을 고쳐야 2번이 드러나는 구조라 엔티티만 읽어서는 둘 다 알 수 없었다.
// 둘 다 #152(Flyway 도입)에서 수정됐고, ddl-auto=validate가 스키마 불일치는 기동 시점에 잡는다.
// 다만 validate는 "저장이 되는지"까지 보지 않으므로(2번은 INSERT 시점 오류다)
// 실제 저장·조회를 한 번 태우는 이 테스트로 회귀를 막는다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommentEmojiPersistenceTest extends PostgresTestSupport {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CommentEmojiRepository commentEmojiRepository;

    private User persistUser(String tag) {
        return em.persist(new User(tag + "@memorin.test", "hash", tag, tag, null));
    }

    private PostComments persistComment(User author) {
        Post post = Post.create(author, "[]", VisibilityType.PUBLIC,
                TimeslotType.AM, Date.valueOf(LocalDate.of(2026, 7, 1)), List.of(TagType.ETC));
        em.persist(post);
        return em.persist(PostComments.of(post, author, null, "이모지 대상 댓글",
                LocalDateTime.of(2026, 7, 1, 9, 0)));
    }

    @Test
    void 댓글_이모지가_네이티브_ENUM_컬럼에_저장된다() {
        // given
        User user = persistUser("emoji-user");
        PostComments comment = persistComment(user);

        // when — 테이블명이나 enum 매핑이 어긋나 있으면 여기서 INSERT가 터진다
        CommentEmoji saved = commentEmojiRepository.save(CommentEmoji.of(user, comment, EmojiType.HEART));
        em.flush();

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmojiType()).isEqualTo(EmojiType.HEART);
    }

    @Test
    void 댓글_목록용_배치_집계가_동작한다() {
        // given — 한 댓글에 서로 다른 유저가 서로 다른 이모지를 단다
        User me = persistUser("me-user");
        User other = persistUser("other-user");
        PostComments comment = persistComment(me);

        commentEmojiRepository.save(CommentEmoji.of(me, comment, EmojiType.HEART));
        commentEmojiRepository.save(CommentEmoji.of(other, comment, EmojiType.HEART));
        commentEmojiRepository.save(CommentEmoji.of(other, comment, EmojiType.FIRE));
        em.flush();

        // when — 댓글 목록에서 쓰는 배치 집계 (댓글마다 조회하지 않기 위한 경로)
        List<EmojiCountDto> counts =
                commentEmojiRepository.countByCommentIds(List.of(comment.getId()), me.getId());

        // then — HEART 2명(내가 누름) / FIRE 1명(내가 안 누름)
        assertThat(counts).hasSize(2);

        EmojiCountDto heart = counts.stream()
                .filter(c -> c.emojiType() == EmojiType.HEART).findFirst().orElseThrow();
        assertThat(heart.count()).isEqualTo(2L);
        assertThat(heart.reactedByMe()).isTrue();

        EmojiCountDto fire = counts.stream()
                .filter(c -> c.emojiType() == EmojiType.FIRE).findFirst().orElseThrow();
        assertThat(fire.count()).isEqualTo(1L);
        assertThat(fire.reactedByMe()).isFalse();
    }
}
