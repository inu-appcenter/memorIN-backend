package com.memorin.domain.posts;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.PostService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 친구 피드(GET /api/posts/friends)가 SQL을 실제로 몇 번 날리는지 센다.
//
// PostService.friendFeed는 지금 쿼리 3개로 끝난다.
//   1. findFollowingIds        — 내가 팔로우한 유저 id 목록
//   2. findFriendFeed          — 그 유저들의 게시물 (user_id IN)
//   3. findByPostIdInOrder...  — 페이지에 든 게시물의 미디어 배치
//
// 이 테스트가 못 박는 것:
//   - 팔로잉 수를 늘려도 쿼리가 늘지 않는가 (작성자별 조회로 쪼개지지 않는가)
//   - 미디어 장수를 늘려도 쿼리가 늘지 않는가 (미디어 배치 조회가 유지되는가)
//
// 응답에 작성자 닉네임·프로필을 추가하면(#145) post.getUser()가 PK 밖 필드를 건드리게 되어
// 게시물 1건당 SELECT 1번이 더 나간다. 그때 이 테스트가 깨지는 것이 의도다. JOIN FETCH로 받을 것.
//
// 주의: 데이터를 심는 트랜잭션과 측정 트랜잭션을 분리한다.
//       같은 트랜잭션에서 재면 1차 캐시가 조회를 가로채 실제 운영에서 나갈 쿼리가 안 보인다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FriendFeedQueryCountTest extends PostgresTestSupport {

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

    // "나"를 만들고, authorCount명을 ACCEPTED로 팔로우한다.
    // 각 작성자는 게시물 1개 + 미디어 mediaPerPost장을 가진다. 내 id를 반환.
    private UUID seedFriendFeed(String tag, int authorCount, int mediaPerPost) {
        return tx.execute(status -> {
            User me = new User(tag + "-me@memorin.test", "hash", tag + "-me", tag + "-me", null);
            em.persist(me);

            for (int a = 0; a < authorCount; a++) {
                String name = "%s-a%d".formatted(tag, a);
                User author = new User(name + "@memorin.test", "hash", name, name, null);
                em.persist(author);

                Follows follows = new Follows(me, author); // 내가 author를 팔로우
                follows.accept();                          // ACCEPTED만 친구 피드에 잡힌다
                em.persist(follows);

                // FRIENDS 공개범위로 둔다 — 친구 피드가 실제로 노출해야 하는 대상
                Post post = Post.create(author, "[]", VisibilityType.FRIENDS,
                        TimeslotType.AM, Date.valueOf(LocalDate.of(2026, 7, 1).plusDays(a)));
                em.persist(post);

                for (int m = 0; m < mediaPerPost; m++) {
                    em.persist(PostMedia.of(post, "uploads/%s-%d-%d.png".formatted(tag, a, m),
                            "image/png", 1000L, (short) m, 100, 100));
                }
            }
            em.flush();
            return me.getId();
        });
    }

    private long countQueriesForFriendFeed(UUID meId, int expectedPosts) {
        Statistics stats = statistics();
        stats.clear();

        PostListResponse response = postService.friendFeed(meId, null, 20);

        assertThat(response.items()).hasSize(expectedPosts);
        return stats.getPrepareStatementCount();
    }

    @Test
    void 팔로잉_수를_늘려도_쿼리는_늘지_않는다() {
        // given — 작성자 수만 3배 차이 (미디어는 게시물당 1장으로 고정)
        UUID few = seedFriendFeed("ff-few", 3, 1);
        UUID many = seedFriendFeed("ff-many", 9, 1);

        // when
        long fewQueries = countQueriesForFriendFeed(few, 3);
        long manyQueries = countQueriesForFriendFeed(many, 9);

        System.out.printf("%n>>> 팔로잉 3명 / 게시물 3개 → SQL %d개%n", fewQueries);
        System.out.printf(">>> 팔로잉 9명 / 게시물 9개 → SQL %d개%n%n", manyQueries);

        // 팔로잉이 3배 늘었는데 쿼리도 따라 늘면 N+1이다.
        assertThat(manyQueries)
                .as("팔로잉 수에 비례해 쿼리가 늘어나면 N+1")
                .isEqualTo(fewQueries);
    }

    @Test
    void 미디어_장수를_늘려도_쿼리는_늘지_않는다() {
        // given — 게시물 수는 같고 미디어 장수만 3배 차이
        UUID few = seedFriendFeed("fm-few", 3, 1);   // 미디어 3장
        UUID many = seedFriendFeed("fm-many", 3, 3); // 미디어 9장

        // when
        long fewQueries = countQueriesForFriendFeed(few, 3);
        long manyQueries = countQueriesForFriendFeed(many, 3);

        System.out.printf("%n>>> 게시물 3개 / 미디어 3장 → SQL %d개%n", fewQueries);
        System.out.printf(">>> 게시물 3개 / 미디어 9장 → SQL %d개%n%n", manyQueries);

        // 미디어가 3배 늘었는데 쿼리도 따라 늘면 N+1이다.
        assertThat(manyQueries)
                .as("미디어 장수에 비례해 쿼리가 늘어나면 N+1")
                .isEqualTo(fewQueries);
    }
}
