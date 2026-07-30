package com.memorin.domain.follows;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.users.dto.UserFollowPageResponse;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.service.UserService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 팔로워 목록 조회가 팔로워 수에 비례해 SQL을 늘리지 않는지(N+1 없음) 실측한다.
// findByFollowingIdAndStatus의 JOIN FETCH가 빠지면 팔로워 1명당 SELECT가 1번씩 더 나가 이 테스트가 깨진다.
//
// 주의: 데이터를 심는 트랜잭션과 측정 트랜잭션을 분리한다(1차 캐시가 조회를 가로채는 것 방지).
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FollowListQueryCountTest extends PostgresTestSupport {

    @DynamicPropertySource
    static void enableStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private Statistics statistics() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    // target을 팔로우하는(ACCEPTED) follower를 followerCount명 만든다. target의 id를 반환.
    private UUID seedFollowers(String tag, int followerCount) {
        return tx.execute(status -> {
            User target = new User(tag + "-target@memorin.test", "hash", tag + "-target", tag + "-target", null);
            em.persist(target);
            for (int i = 0; i < followerCount; i++) {
                User follower = new User("%s-f%d@memorin.test".formatted(tag, i), "hash",
                        "%s-f%d".formatted(tag, i), "%s-f%d".formatted(tag, i), null);
                em.persist(follower);
                Follows follows = new Follows(follower, target);
                follows.accept(); // ACCEPTED만 목록에 잡힘
                em.persist(follows);
            }
            em.flush();
            return target.getId();
        });
    }

    private long countQueriesForFollowers(UUID targetId, int expected) {
        Statistics stats = statistics();
        stats.clear();

        UserFollowPageResponse response = userService.getFollowers(targetId, null, 20);

        assertThat(response.items()).hasSize(expected);
        return stats.getPrepareStatementCount();
    }

    @Test
    void 팔로워_수를_늘려도_쿼리는_늘지_않는다() {
        // given — 팔로워 수만 3배 차이
        UUID few = seedFollowers("few", 3);
        UUID many = seedFollowers("many", 9);

        // when
        long fewQueries = countQueriesForFollowers(few, 3);
        long manyQueries = countQueriesForFollowers(many, 9);

        System.out.printf("%n>>> 팔로워 3명 → SQL %d개%n", fewQueries);
        System.out.printf(">>> 팔로워 9명 → SQL %d개%n%n", manyQueries);

        // 팔로워가 3배 늘었는데 쿼리도 따라 늘면 N+1이다.
        assertThat(manyQueries)
                .as("팔로워 수에 비례해 쿼리가 늘어나면 N+1")
                .isEqualTo(fewQueries);
    }
}
