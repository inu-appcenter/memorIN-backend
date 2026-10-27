package com.memorin.domain.follows;

import com.memorin.domain.follows.dto.FollowRecommendationListResponse;
import com.memorin.domain.follows.dto.FollowRecommendationResponse;
import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.service.FollowService;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FollowRecommendationTest extends PostgresTestSupport {

    @Autowired
    private FollowService followService;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate tx;

    // recommend()는 size를 넘겨도 MAX_PAGE_SIZE(=50)로 잘린다 — 실제 유효 상한을 그대로 요청한다.
    private static final int MAX_PAGE_SIZE = 50;

    @Test
    void 추천_목록에는_요청자_본인이_포함되지_않는다() {
        UUID[] ids = new UUID[1];
        tx.execute(status -> {
            User viewer = persistUser("viewer-self");
            ids[0] = viewer.getId();
            return null;
        });

        FollowRecommendationListResponse response = followService.recommend(ids[0], null, MAX_PAGE_SIZE);

        assertThat(response.items())
            .extracting(FollowRecommendationResponse::userId)
            .doesNotContain(ids[0]);
    }

    @Test
    void 이미_ACCEPTED_상태로_팔로우_중인_사용자는_추천되지_않는다() {
        UUID[] ids = new UUID[2];
        tx.execute(status -> {
            User viewer = persistUser("viewer-accepted");
            User alreadyFriend = persistUser("already-friend");
            persistAcceptedFollow(viewer, alreadyFriend);
            ids[0] = viewer.getId();
            ids[1] = alreadyFriend.getId();
            return null;
        });

        FollowRecommendationListResponse response = followService.recommend(ids[0], null, MAX_PAGE_SIZE);

        assertThat(response.items())
            .extracting(FollowRecommendationResponse::userId)
            .doesNotContain(ids[1]);
    }

    @Test
    void PENDING_상태로_요청을_보냈거나_받은_사용자는_추천되지_않는다() {
        UUID[] ids = new UUID[3];
        tx.execute(status -> {
            User viewer = persistUser("viewer-pending");
            User sentTo = persistUser("pending-sent-to");
            User receivedFrom = persistUser("pending-received-from");
            persistPendingFollow(viewer, sentTo);       // 내가 보낸 요청
            persistPendingFollow(receivedFrom, viewer); // 내가 받은 요청
            ids[0] = viewer.getId();
            ids[1] = sentTo.getId();
            ids[2] = receivedFrom.getId();
            return null;
        });

        FollowRecommendationListResponse response = followService.recommend(ids[0], null, MAX_PAGE_SIZE);

        assertThat(response.items())
            .extracting(FollowRecommendationResponse::userId)
            .doesNotContain(ids[1], ids[2]);
    }

    @Test
    void BLOCKED_관계인_사용자는_추천되지_않는다() {
        UUID[] ids = new UUID[2];
        tx.execute(status -> {
            User viewer = persistUser("viewer-blocked");
            User blocked = persistUser("blocked-user");
            persistBlockedFollow(viewer, blocked);
            ids[0] = viewer.getId();
            ids[1] = blocked.getId();
            return null;
        });

        FollowRecommendationListResponse response = followService.recommend(ids[0], null, MAX_PAGE_SIZE);

        assertThat(response.items())
            .extracting(FollowRecommendationResponse::userId)
            .doesNotContain(ids[1]);
    }

    @Test
    void 공통_친구_수가_많을수록_추천_순위가_높다() {
        UUID[] ids = new UUID[5]; // 0=viewer, 1,2=friend1/2(미사용 참조용), 3=high, 4=low
        tx.execute(status -> {
            User viewer = persistUser("viewer-mutual");
            User friend1 = persistUser("mutual-friend-1");
            User friend2 = persistUser("mutual-friend-2");
            User candidateHigh = persistUser("mutual-candidate-high"); // 공통 친구 2명
            User candidateLow = persistUser("mutual-candidate-low");   // 공통 친구 1명

            persistAcceptedFollow(viewer, friend1);
            persistAcceptedFollow(viewer, friend2);

            persistAcceptedFollow(candidateHigh, friend1);
            persistAcceptedFollow(candidateHigh, friend2);
            persistAcceptedFollow(candidateLow, friend1);

            ids[0] = viewer.getId();
            ids[3] = candidateHigh.getId();
            ids[4] = candidateLow.getId();
            return null;
        });

        FollowRecommendationListResponse response = followService.recommend(ids[0], null, MAX_PAGE_SIZE);

        List<UUID> ranked = response.items().stream()
            .map(FollowRecommendationResponse::userId)
            .filter(id -> id.equals(ids[3]) || id.equals(ids[4]))
            .toList();

        assertThat(ranked).containsExactly(ids[3], ids[4]);
    }

    @Test
    void 공통_친구_수가_같으면_최근_게시물이_있는_사용자가_더_상위로_추천된다() {
        UUID[] ids = new UUID[3];
        tx.execute(status -> {
            User viewer = persistUser("viewer-recency");
            User candidateActive = persistUser("recency-active");     // 공통 친구 0, 오늘 게시물 있음
            User candidateInactive = persistUser("recency-inactive"); // 공통 친구 0, 게시물 없음

            persistPost(candidateActive, LocalDate.now());

            ids[0] = viewer.getId();
            ids[1] = candidateActive.getId();
            ids[2] = candidateInactive.getId();
            return null;
        });

        FollowRecommendationListResponse response = followService.recommend(ids[0], null, MAX_PAGE_SIZE);

        List<UUID> ranked = response.items().stream()
            .map(FollowRecommendationResponse::userId)
            .filter(id -> id.equals(ids[1]) || id.equals(ids[2]))
            .toList();

        assertThat(ranked).containsExactly(ids[1], ids[2]);
    }

    @Test
    void 커서_페이지네이션은_후보군을_중복이나_누락_없이_순회한다() {
        UUID[] ids = new UUID[6]; // 0=viewer, 1~5=candidate(공통친구 4,3,2,1,0명)
        tx.execute(status -> {
            User viewer = persistUser("viewer-cursor");
            User[] friends = new User[4];
            for (int i = 0; i < 4; i++) {
                friends[i] = persistUser("cursor-friend-" + i);
                persistAcceptedFollow(viewer, friends[i]);
            }

            for (int c = 0; c < 5; c++) {
                User candidate = persistUser("cursor-candidate-" + c);
                // c번째 후보는 공통 친구를 (4-c)명 가진다 → mutual_count 4,3,2,1,0 → score 40,30,20,10,0
                for (int f = 0; f < 4 - c; f++) {
                    persistAcceptedFollow(candidate, friends[f]);
                }
                ids[c + 1] = candidate.getId();
            }
            ids[0] = viewer.getId();
            return null;
        });

        // 스코핑 장치가 없는 쿼리라 전체 순회 범위가 "이 테스트 실행 동안 커밋된 사용자 전체"다.
        // PostgresTestSupport가 테스트 스위트 단위로 격리된(testcontainers 등) DB를 쓴다는 전제 하에,
        // 한 스위트 안에서 만들어지는 사용자 수는 충분히 작아 size=5 페이징으로도 빠르게 끝난다.
        List<UUID> collected = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        int guard = 0;
        while (hasNext && guard++ < 200) {
            FollowRecommendationListResponse response = followService.recommend(ids[0], cursor, 5);
            response.items().forEach(item -> collected.add(item.userId()));
            cursor = response.nextCursor();
            hasNext = response.hasNext();
        }

        List<UUID> expectedOrder = List.of(ids[1], ids[2], ids[3], ids[4], ids[5]);
        List<UUID> actualKnownOrder = collected.stream()
            .filter(expectedOrder::contains)
            .toList();

        assertThat(actualKnownOrder).containsExactlyElementsOf(expectedOrder);
        assertThat(actualKnownOrder).doesNotHaveDuplicates();
    }

    private User persistUser(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User user = new User(
            "test-" + unique + "-" + label + "@example.com",
            "hashed-password",
            "user-" + unique,
            label,
            null
        );
        em.persist(user);
        return user;
    }

    private void persistAcceptedFollow(User follower, User following) {
        Follows follow = new Follows(follower, following);
        follow.accept();
        em.persist(follow);
    }

    private void persistPendingFollow(User follower, User following) {
        em.persist(new Follows(follower, following));
    }

    private void persistBlockedFollow(User follower, User following) {
        Follows follow = new Follows(follower, following);
        follow.block();
        em.persist(follow);
    }

    private void persistPost(User owner, LocalDate recordedDate) {
        Post post = Post.create(owner, "{}", VisibilityType.PUBLIC, TimeslotType.AM, Date.valueOf(recordedDate), List.of());
        em.persist(post);
    }
}
