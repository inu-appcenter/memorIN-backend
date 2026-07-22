package com.memorin.domain.posts;

import com.memorin.domain.post_media.Entity.PostMedia;
import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.posts.Entity.TimeslotType;
import com.memorin.domain.posts.Entity.VisibilityType;
import com.memorin.domain.posts.Service.PostService;
import com.memorin.domain.posts.dto.Response.PostListResponse;
import com.memorin.domain.users.Entity.User;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 피드 조회가 SQL을 실제로 몇 번 날리는지 센다.
// N+1은 코드 모양이 아니라 실행된 SQL 개수로 판단해야 한다.
//
// 주의: 데이터를 심는 트랜잭션과 측정하는 트랜잭션은 반드시 분리해야 한다.
//       같은 트랜잭션 안에서 재면 1차 캐시(영속성 컨텍스트)가 조회를 가로채
//       실제 운영에서 나갈 쿼리가 안 보인다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostFeedQueryCountTest extends PostgresTestSupport {

    @DynamicPropertySource
    static void enableStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private PostService postService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private Statistics statistics() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private UUID seed(String tag, int posts, int mediaPerPost) {
        return tx.execute(status -> {
            User author = new User(tag + "@memorin.test", "hash", tag, tag, null);
            em.persist(author);
            for (int p = 0; p < posts; p++) {
                Post post = Post.create(author, "[]", VisibilityType.PUBLIC,
                        TimeslotType.AM, Date.valueOf(LocalDate.of(2026, 7, 1).plusDays(p)));
                em.persist(post);
                for (int m = 0; m < mediaPerPost; m++) {
                    em.persist(PostMedia.of(post, "uploads/%s-%d-%d.png".formatted(tag, p, m),
                            "image/png", 1000L, (short) m, 100, 100));
                }
            }
            em.flush();
            return author.getId();
        });
    }

    private long countQueriesForFeed(UUID authorId, int expectedPosts) {
        Statistics stats = statistics();
        stats.clear();

        PostListResponse response = postService.list(authorId, authorId, null, 20);

        assertThat(response.items()).hasSize(expectedPosts);
        return stats.getPrepareStatementCount();
    }

    @Test
    void 미디어_장수를_늘리면_쿼리도_늘어나는지_본다() {
        // given — 게시물 수는 같고 미디어 장수만 3배 차이
        UUID few = seed("few", 5, 1);    // 미디어 5장
        UUID many = seed("many", 5, 3);  // 미디어 15장

        // when
        long fewQueries = countQueriesForFeed(few, 5);
        long manyQueries = countQueriesForFeed(many, 5);

        System.out.printf("%n>>> 게시물 5개 / 미디어 5장  → SQL %d개%n", fewQueries);
        System.out.printf(">>> 게시물 5개 / 미디어 15장 → SQL %d개%n%n", manyQueries);

        // 미디어가 3배 늘었는데 쿼리도 따라 늘면 N+1이다.
        assertThat(manyQueries)
                .as("미디어 장수에 비례해 쿼리가 늘어나면 N+1")
                .isEqualTo(fewQueries);
    }
}
