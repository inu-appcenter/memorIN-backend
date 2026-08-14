package com.memorin.domain.follows;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.users.dto.UserFollowPageResponse;
import com.memorin.domain.users.dto.UserFollowResponse;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.service.UserService;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 팔로워/팔로잉 목록의 커서 페이지네이션이 실제로 동작하는지 고정한다.
//
// 왜 따로 필요한가: FollowListQueryCountTest는 SQL "개수"만 센다. 커서를 무시하고
// 전체를 로드해도 쿼리 개수는 1개라 그 테스트는 통과한다. 실제로 예전에 cursor·size를
// 통째로 무시하던 버전이 그 테스트를 통과한 채로 머지된 적이 있다.
// 여기서는 페이지를 실제로 넘겨보고 결과 집합으로 검증한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FollowCursorPaginationTest extends PostgresTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    // target을 팔로우하는(ACCEPTED) follower를 count명 만들고 target id를 반환.
    private UUID seedFollowers(String tag, int count) {
        return tx.execute(status -> {
            User target = new User(tag + "-target@memorin.test", "hash", tag + "-target", tag + "-target", null);
            em.persist(target);

            for (int i = 0; i < count; i++) {
                User follower = new User("%s-f%d@memorin.test".formatted(tag, i), "hash",
                        "%s-f%d".formatted(tag, i), "%s-f%d".formatted(tag, i), null);
                em.persist(follower);
                Follows follows = new Follows(follower, target);
                follows.accept();
                em.persist(follows);
            }

            em.flush();
            return target.getId();
        });
    }

    @Test
    void 커서로_페이지를_넘기면_중복_없이_전부_조회된다() {
        // given — 팔로워 10명, 페이지 크기 3 → 4페이지(3+3+3+1)여야 한다
        UUID targetId = seedFollowers("paging", 10);

        // when — hasNext가 false가 될 때까지 커서를 따라간다
        List<UUID> collected = new ArrayList<>();
        UUID cursor = null;
        int pages = 0;

        do {
            UserFollowPageResponse page = userService.getFollowers(targetId, cursor, 3);
            pages++;

            assertThat(page.items())
                    .as("페이지 크기가 요청한 size를 넘으면 안 된다")
                    .hasSizeLessThanOrEqualTo(3);

            for (UserFollowResponse item : page.items()) {
                collected.add(item.id());
            }

            cursor = page.nextCursor();

            assertThat(pages)
                    .as("hasNext가 계속 true면 커서가 안 움직이는 것 — 무한 루프 방지")
                    .isLessThanOrEqualTo(10);

            if (!page.hasNext()) break;

            assertThat(cursor)
                    .as("hasNext가 true인데 nextCursor가 null이면 FE가 다음 페이지를 못 받는다")
                    .isNotNull();
        } while (true);

        // then
        assertThat(pages).as("팔로워 10명 / 페이지 3 → 4페이지").isEqualTo(4);
        assertThat(collected).as("중복 없이").doesNotHaveDuplicates();
        assertThat(collected).as("빠짐 없이 10명 전부").hasSize(10);
    }

    @Test
    void size를_무시하고_전체를_반환하지_않는다() {
        // given — 팔로워 30명
        UUID targetId = seedFollowers("size", 30);

        // when
        UserFollowPageResponse page = userService.getFollowers(targetId, null, 5);

        // then — 커서를 무시하고 전체를 로드하던 회귀를 잡는다
        assertThat(page.items()).hasSize(5);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void size가_비정상이어도_500이_되지_않는다() {
        // given
        UUID targetId = seedFollowers("clamp", 5);

        // when — 음수는 PageRequest.of가 예외를 던져 500이 되던 값이다
        UserFollowPageResponse negative = userService.getFollowers(targetId, null, -1);
        UserFollowPageResponse huge = userService.getFollowers(targetId, null, 1_000_000);

        // then — MAX_PAGE_SIZE(50)로 잘린다
        assertThat(negative.items()).isNotEmpty();
        assertThat(huge.items()).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void 마지막_페이지는_hasNext가_false다() {
        // given — 팔로워 3명, 페이지 크기 3 → 딱 떨어진다
        UUID targetId = seedFollowers("exact", 3);

        // when
        UserFollowPageResponse page = userService.getFollowers(targetId, null, 3);

        // then — 경계값에서 hasNext가 true로 새면 FE가 빈 페이지를 한 번 더 부른다
        assertThat(page.items()).hasSize(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }
}
