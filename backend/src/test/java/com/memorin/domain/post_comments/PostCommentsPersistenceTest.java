package com.memorin.domain.post_comments;

import com.memorin.domain.post_comments.Entity.PostComments;
import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.posts.Entity.TimeslotType;
import com.memorin.domain.posts.Entity.VisibilityType;
import com.memorin.domain.users.Entity.User;
import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 이슈 #40 검증: post_comments.parent_id는 users가 아니라 post_comments 자기참조이고,
// 최상위 댓글은 parent_id가 NULL이어야 한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostCommentsPersistenceTest extends PostgresTestSupport {

    @Autowired
    private TestEntityManager em;

    private User persistUser(String suffix) {
        User user = new User(
                "tester" + suffix + "@memorin.test",
                "hashed-password",
                "tester" + suffix,
                "테스터" + suffix,
                null
        );
        return em.persist(user);
    }

    private Post persistPost(User author) {
        Post post = Post.create(
                author,
                "[]",
                VisibilityType.PUBLIC,
                TimeslotType.values()[0],
                Date.valueOf(LocalDate.of(2026, 7, 20))
        );
        return em.persist(post);
    }

    @Test
    void 최상위_댓글은_parent가_없이_저장된다() {
        // given
        User author = persistUser("1");
        Post post = persistPost(author);

        // when
        PostComments saved = em.persist(PostComments.of(post, author, null, "최상위 댓글"));
        em.flush();
        em.clear();

        // then
        PostComments found = em.find(PostComments.class, saved.getId());
        assertThat(found.getParentId()).isNull();
        assertThat(found.getBody()).isEqualTo("최상위 댓글");
    }

    @Test
    void 대댓글은_부모_댓글을_자기참조로_가진다() {
        // given
        User author = persistUser("2");
        Post post = persistPost(author);
        PostComments parent = em.persist(PostComments.of(post, author, null, "부모 댓글"));

        // when
        PostComments saved = em.persist(PostComments.of(post, author, parent, "대댓글"));
        em.flush();
        em.clear();

        // then
        PostComments found = em.find(PostComments.class, saved.getId());
        // parent_id 타입이 User였다면 이 단언 자체가 컴파일되지 않는다.
        assertThat(found.getParentId()).isNotNull();
        assertThat(found.getParentId().getId()).isEqualTo(parent.getId());
        assertThat(found.getParentId().getBody()).isEqualTo("부모 댓글");
    }
}
