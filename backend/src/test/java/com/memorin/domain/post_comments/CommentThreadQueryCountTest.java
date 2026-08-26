package com.memorin.domain.post_comments;

import com.memorin.domain.emoji.entity.CommentEmoji;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.post_comments.dto.response.PostCommentResponse;
import com.memorin.domain.post_comments.entity.PostComments;
import com.memorin.domain.post_comments.service.PostCommentService;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 댓글 스레드 조회가 SQL을 실제로 몇 번 날리는지 센다. (docs/n+1-audit.md §3-2 패턴)
//
// 이 테스트가 지키는 것은 두 가지다.
//  1. 작성자 정보(닉네임·프로필)를 응답에 넣으면서 c.getUser() 프록시가 초기화된다.
//     JOIN FETCH가 빠지면 댓글 1건당 users SELECT가 1번씩 붙는다.
//  2. 이모지 집계를 댓글마다 따로 조회하면 같은 모양의 N+1이 된다.
//
// 둘 다 "코드 모양"으로는 안 보이고 실행된 SQL 개수로만 드러난다.
//
// 주의: 데이터를 심는 트랜잭션과 측정하는 트랜잭션은 반드시 분리한다.
//       같은 트랜잭션에서 재면 1차 캐시가 조회를 가로채 실제 쿼리가 안 보인다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommentThreadQueryCountTest extends PostgresTestSupport {

    @DynamicPropertySource
    static void enableStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private PostCommentService postCommentService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private UUID readerId;

    private Statistics statistics() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    // 댓글 commentCount개를 심고, 각 댓글에 서로 다른 사용자가 이모지를 하나씩 단다.
    // 댓글마다 작성자를 따로 두는 게 핵심이다 — 작성자가 하나면 1차 캐시에 걸려 N+1이 숨는다.
    private UUID seed(String tag, int commentCount) {
        return tx.execute(status -> {
            User author = persistUser(tag + "-author");
            if (readerId == null) {
                readerId = persistUser(tag + "-reader").getId();
            }

            Post post = Post.create(author, "[]", VisibilityType.PUBLIC, TimeslotType.AM,
                    Date.valueOf(LocalDate.of(2026, 8, 1)),
                List.of(com.memorin.domain.posts.entity.TagType.ECT));
            em.persist(post);

            for (int i = 0; i < commentCount; i++) {
                User commenter = persistUser(tag + "-commenter-" + i);
                PostComments comment = PostComments.of(post, commenter, null, "댓글 " + i,
                        LocalDateTime.of(2026, 8, 1, 9, 0).plusMinutes(i));
                em.persist(comment);

                EmojiType type = EmojiType.values()[i % EmojiType.values().length];
                em.persist(CommentEmoji.of(commenter, comment, type));
            }

            em.flush();
            return post.getId();
        });
    }

    private User persistUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        em.persist(user);
        return user;
    }

    private long countQueriesForThread(UUID postId, int expectedComments) {
        Statistics stats = statistics();
        stats.clear();

        List<PostCommentResponse> thread = postCommentService.getThread(postId, readerId);

        assertThat(thread).hasSize(expectedComments);
        // 응답을 실제로 읽어 프록시 초기화를 유발한다. 필드를 안 건드리면 N+1이 숨는다.
        assertThat(thread).allSatisfy(c -> {
            assertThat(c.authorDisplayName()).isNotBlank();
            assertThat(c.emojis()).isNotEmpty();
        });

        return stats.getPrepareStatementCount();
    }

    @Test
    void 댓글_수를_늘려도_쿼리_수는_그대로다() {
        // given — 댓글 수만 3배 차이
        UUID few = seed("few" + UUID.randomUUID().toString().substring(0, 6), 3);
        UUID many = seed("many" + UUID.randomUUID().toString().substring(0, 6), 9);

        // when
        long fewQueries = countQueriesForThread(few, 3);
        long manyQueries = countQueriesForThread(many, 9);

        System.out.printf("%n>>> 댓글 3개 → SQL %d개%n", fewQueries);
        System.out.printf(">>> 댓글 9개 → SQL %d개%n%n", manyQueries);

        assertThat(manyQueries)
                .as("댓글 수에 비례해 쿼리가 늘어나면 N+1 (작성자 JOIN FETCH 또는 이모지 배치 집계가 빠졌다)")
                .isEqualTo(fewQueries);

        // 게시물 1 + 스레드 1 + 이모지 집계 1 = 3.
        // 늘어났다면 어딘가에서 조회가 하나 더 붙은 것이니 이유를 확인하고 이 숫자를 갱신할 것.
        assertThat(fewQueries)
                .as("PUBLIC 게시물 스레드 조회의 기대 쿼리 수")
                .isEqualTo(3);
    }

    // 소프트 삭제는 body를 null로 비운다. body가 NOT NULL이면 이 경로는 500으로 터진다.
    // 실제로 그랬고(V5 마이그레이션), 삭제 경로를 실행하는 테스트가 없어 드러나지 않았다.
    // 엔티티를 직접 만지지 않고 서비스 메서드를 그대로 호출해 실제 API 경로를 태운다.
    @Test
    void 삭제된_댓글은_작성자_정보를_감춘다() {
        UUID postId = seed("tomb" + UUID.randomUUID().toString().substring(0, 6), 2);

        UUID[] target = tx.execute(status -> {
            PostComments oldest = em.createQuery(
                            "SELECT c FROM PostComments c WHERE c.post.id = :postId ORDER BY c.createdAt ASC",
                            PostComments.class)
                    .setParameter("postId", postId)
                    .setMaxResults(1)
                    .getSingleResult();
            return new UUID[]{oldest.getId(), oldest.getUser().getId()};
        });

        postCommentService.delete(target[0], target[1]); // 작성자 본인이 삭제

        List<PostCommentResponse> thread = postCommentService.getThread(postId, readerId);

        PostCommentResponse deleted = thread.get(0);
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.body()).isEqualTo("삭제된 댓글입니다.");
        assertThat(deleted.authorId()).isNull();
        assertThat(deleted.authorUsername()).isNull();
        assertThat(deleted.authorDisplayName()).isNull();
        assertThat(deleted.authorProfileImageKey()).isNull();
    }
}
