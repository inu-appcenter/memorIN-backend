package com.memorin.domain.posts;


import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostSummaryResponse;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.RecommendedFeedService;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecommendedFeedQueryTest extends PostgresTestSupport {

    @DynamicPropertySource
    static void enableStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private RecommendedFeedService recommendedFeedService;
    @Autowired private TransactionTemplate tx;
    @PersistenceContext
    private EntityManager em;

    private Statistics statistics() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    // 추천 피드는 특정 작성자가 아니라 "최근 전체공개 글" 전체를 후보로 삼는다.
    // 컨테이너를 공유하는 다른 테스트의 게시물도 후보에 섞이므로,
    // 조회수를 크게 줘 점수 상위에 오도록 하고 assert는 내가 심은 id로만 한다.
    private List<UUID> seedPosts(String tag, int posts, int mediaPerPost) {
        return tx.execute(status -> {
            User author = new User(tag + "@memorin.test", "hash", tag, tag, null);
            em.persist(author);

            List<UUID> ids = new ArrayList<>();
            for (int p = 0; p < posts; p++) {
                Post post = Post.builder()
                        .user(author)
                        .content("[]")
                        .visibilityType(VisibilityType.PUBLIC)
                        .timeslot(TimeslotType.AM)
                        .recordedDate(Date.valueOf(LocalDate.now()))
                        .viewCount(10_000)
                        .build();
                em.persist(post);
                ids.add(post.getId());

                addMedia(post, tag, 0, mediaPerPost);
            }
            em.flush();
            return ids;
        });
    }

    private void addMedia(Post post, String tag, int from, int to) {
        for (int m = from; m < to; m++) {
            em.persist(PostMedia.of(post, "uploads/%s-%s-%d.png".formatted(tag, post.getId(), m),
                    "image/png", 1000L, (short) m, 100, 100));
        }
    }

    private PostSummaryResponse findItem(PostListResponse response, UUID postId) {
        return response.items().stream()
                .filter(i -> i.postId().equals(postId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("추천 피드에 심은 게시물이 없다: " + postId));
    }

    @Test
    void 추천_피드_조회가_예외없이_동작하고_공개글을_돌려준다() {
        UUID postId = seedPosts("reco", 1, 0).get(0);

        PostListResponse response = recommendedFeedService.getRecommendedFeed(null, 50);

        assertThat(response.items()).isNotEmpty();
        assertThat(findItem(response, postId).visibility()).isEqualTo(VisibilityType.PUBLIC);
    }

    // 이전 구현은 PostSummaryResponse.of(post, List.of())로 첨부를 통째로 비워 내보냈다.
    // 사진이 본체인 서비스에서 피드에 사진이 없으면 화면을 그릴 수 없다.
    @Test
    void 추천_피드가_첨부_미디어를_함께_내려준다() {
        UUID postId = seedPosts("reco-media", 1, 2).get(0);

        PostListResponse response = recommendedFeedService.getRecommendedFeed(null, 50);

        assertThat(findItem(response, postId).attachments())
                .as("첨부가 비어 있으면 FE가 피드에 사진을 그릴 수 없다")
                .hasSize(2);
    }

    // 미디어를 게시물마다 따로 조회하면 N+1이다.
    // 게시물 수를 고정한 채 미디어만 늘려 쿼리 수가 그대로인지 본다
    // (게시물을 새로 심으면 후보 풀 자체가 달라져 비교가 성립하지 않는다).
    @Test
    void 미디어_장수를_늘려도_쿼리는_늘지_않는다() {
        List<UUID> postIds = seedPosts("reco-n1", 5, 1);

        long before = countQueries();

        tx.execute(status -> {
            for (UUID postId : postIds) {
                addMedia(em.find(Post.class, postId), "reco-n1", 1, 3); // 장당 1 → 3
            }
            em.flush();
            return null;
        });

        long after = countQueries();

        System.out.printf("%n>>> 미디어 5장 → SQL %d개 / 15장 → SQL %d개%n%n", before, after);

        assertThat(after)
                .as("미디어 장수에 비례해 쿼리가 늘어나면 N+1")
                .isEqualTo(before);
    }

    private long countQueries() {
        Statistics stats = statistics();
        stats.clear();
        recommendedFeedService.getRecommendedFeed(null, 50);
        return stats.getPrepareStatementCount();
    }
}
