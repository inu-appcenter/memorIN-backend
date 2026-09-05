package com.memorin.domain.posts;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.posts.dto.request.PostSearchRequest;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostSummaryResponse;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.PostSortType;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.PostService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 태그/키워드 검색 API의 "결과가 새거나 틀리면 안 되는" 경로를 검증한다.
//
// 주의: search()는 작성자로 범위를 좁히지 않고(PUBLIC/친구공개 판정은 PostAccessPolicy와 동일 규칙),
// 이 테스트들은 데이터를 롤백 없이 커밋한다. 날짜 범위 필터가 없어서(정렬 기준으로 대체됨),
// 테스트마다 고유한 마커 문자열을 게시물 content에 심고 keyword로 같이 넘겨서 격리한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostSearchTest extends PostgresTestSupport {

    @Autowired
    private PostService postService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private User persistUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        em.persist(user);
        return user;
    }

    private Post persistPost(User owner, String content, VisibilityType visibility, TimeslotType timeslot,
                             LocalDate recordedDate, int viewCount, List<TagType> tags) {
        Post post = Post.create(owner, content, visibility, timeslot, Date.valueOf(recordedDate), tags);
        em.persist(post);
        for (int i = 0; i < viewCount; i++) {
            post.increaseViewCount();
        }
        return post;
    }

    private void persistAcceptedFollow(User follower, User following) {
        Follows follow = new Follows(follower, following);
        follow.accept();
        em.persist(follow);
    }

    private String uniqueMarker() {
        return "marker" + UUID.randomUUID().toString().replace("-", "");
    }

    private String contentWith(String... occurrences) {
        return "[\"" + String.join(" ", occurrences) + "\"]";
    }

    @Test
    void 비공개_게시물은_소유자가_아니면_검색결과에_나오지_않는다() {
        String marker = uniqueMarker();
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User stranger = persistUser("stranger" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, contentWith(marker), VisibilityType.PRIVATE, TimeslotType.AM,
                LocalDate.of(2030, 1, 1), 0, List.of(TagType.TRAVEL));
            em.flush();
            return new UUID[]{owner.getId(), stranger.getId()};
        });

        PostSearchRequest scoped = new PostSearchRequest(marker, null, null, null);

        PostListResponse asStranger = postService.search(ids[1], scoped, null, 20);
        PostListResponse asOwner = postService.search(ids[0], scoped, null, 20);

        assertThat(asStranger.items())
            .as("타인의 비공개 게시물은 어떤 필터로도 보이면 안 된다")
            .isEmpty();
        assertThat(asOwner.items())
            .as("본인 게시물은 비공개여도 자신에게는 보여야 한다")
            .hasSize(1);
    }

    @Test
    void 삭제된_게시물은_공개글이어도_검색결과에_나오지_않는다() {
        String marker = uniqueMarker();
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 2, 1), 0, List.of(TagType.TRAVEL));
            post.softDelete();
            em.flush();
            return owner.getId();
        });

        PostSearchRequest scoped = new PostSearchRequest(marker, null, null, null);

        PostListResponse result = postService.search(ownerId, scoped, null, 20);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void 태그_필터는_지정한_태그를_모두_가진_게시물만_AND로_매칭한다() {
        String marker = uniqueMarker();
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 3, 1), 0, List.of(TagType.TRAVEL));
            persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 3, 1), 0, List.of(TagType.TRAVEL, TagType.FOOD));
            persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 3, 1), 0, List.of(TagType.FOOD));
            em.flush();
            return owner.getId();
        });

        PostSearchRequest condition = new PostSearchRequest(
            marker, List.of(TagType.TRAVEL, TagType.FOOD), null, null);

        PostListResponse result = postService.search(ownerId, condition, null, 20);

        assertThat(result.items())
            .as("여행과 음식을 '둘 다' 가진 게시물만 나와야 한다 (OR가 아니라 AND)")
            .hasSize(1);
        assertThat(result.items().get(0).tagTypes())
            .containsExactlyInAnyOrder(TagType.TRAVEL, TagType.FOOD);
    }

    @Test
    void 여러_필터는_모두_만족해야_결과에_포함된다() {
        String marker = uniqueMarker();
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.PM,
                LocalDate.of(2030, 5, 1), 0, List.of(TagType.TRAVEL));
            persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 5, 1), 0, List.of(TagType.TRAVEL));
            em.flush();
            return owner.getId();
        });

        PostSearchRequest condition = new PostSearchRequest(
            marker, List.of(TagType.TRAVEL), TimeslotType.AM, null);

        PostListResponse result = postService.search(ownerId, condition, null, 20);

        assertThat(result.items())
            .as("태그는 맞아도 timeslot이 안 맞으면 제외돼야 한다 (조건은 AND)")
            .hasSize(1);
    }

    @Test
    void 커서_페이지네이션은_전체_데이터를_중복이나_누락_없이_순회한다() {
        String marker = uniqueMarker();
        tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            for (int i = 0; i < 5; i++) {
                persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                    LocalDate.of(2030, 6, 1), 0, List.of(TagType.DAILY));
            }
            em.flush();
            return null;
        });

        UUID viewerId = UUID.randomUUID(); // PUBLIC + marker로만 스코프되므로 조회자가 누구든 무방
        PostSearchRequest condition = new PostSearchRequest(marker, null, null, null);

        List<UUID> collected = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        int guard = 0;

        while (hasNext) {
            if (++guard > 10) {
                throw new IllegalStateException("페이지네이션이 끝나지 않는다 — 무한 루프 방지용 가드에 걸림");
            }
            PostListResponse page = postService.search(viewerId, condition, cursor, 2);
            page.items().forEach(item -> collected.add(item.postId()));
            cursor = page.nextCursor();
            hasNext = page.hasNext();
        }

        assertThat(collected)
            .as("전체 5개를 중복/누락 없이 순회해야 한다")
            .hasSize(5)
            .doesNotHaveDuplicates();
    }

    @Test
    void keyword가_content에_포함된_게시물만_검색된다() {
        String marker = uniqueMarker();
        String other = uniqueMarker();
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 7, 1), 0, List.of());
            persistPost(owner, contentWith(other), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 7, 1), 0, List.of());
            em.flush();
            return owner.getId();
        });

        PostSearchRequest condition = new PostSearchRequest(marker, null, null, null);

        PostListResponse result = postService.search(ownerId, condition, null, 20);

        assertThat(result.items())
            .as("keyword가 content에 없는 게시물은 결과에 섞이면 안 된다")
            .hasSize(1);
    }

    @Test
    void keyword_검색은_대소문자를_구분하지_않는다() {
        String marker = uniqueMarker();
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, contentWith(marker.toUpperCase()), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 7, 2), 0, List.of());
            em.flush();
            return owner.getId();
        });

        PostSearchRequest condition = new PostSearchRequest(marker.toLowerCase(), null, null, null);

        PostListResponse result = postService.search(ownerId, condition, null, 20);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void keyword의_LIKE_와일드카드_문자는_리터럴로_취급된다() {
        String marker = uniqueMarker();
        String literalKeyword = marker + "-50%off";
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, contentWith(marker + "-50Xoff"), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 7, 3), 0, List.of());
            persistPost(owner, contentWith(literalKeyword), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 7, 3), 0, List.of());
            em.flush();
            return owner.getId();
        });

        PostSearchRequest condition = new PostSearchRequest(literalKeyword, null, null, null);

        PostListResponse result = postService.search(ownerId, condition, null, 20);

        assertThat(result.items())
            .as("'%'는 와일드카드가 아니라 리터럴 문자로 취급되어야 한다")
            .hasSize(1);
    }

    @Test
    void ACCURACY_DESC_정렬은_키워드_등장_빈도가_높은_게시물이_먼저_나온다() {
        String marker = uniqueMarker();
        UUID[] result = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post low = persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 7, 4), 0, List.of());
            Post high = persistPost(owner, contentWith(marker, marker, marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 7, 4), 0, List.of());
            em.flush();
            return new UUID[]{owner.getId(), high.getId(), low.getId()};
        });
        UUID ownerId = result[0];
        UUID highId = result[1];
        UUID lowId = result[2];

        PostSearchRequest condition = new PostSearchRequest(marker, null, null, PostSortType.ACCURACY_DESC);

        PostListResponse page = postService.search(ownerId, condition, null, 20);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).postId())
            .as("키워드가 3번 등장한 게시물이 1번 등장한 게시물보다 먼저 나와야 한다")
            .isEqualTo(highId);
        assertThat(page.items().get(1).postId()).isEqualTo(lowId);
    }

    @Test
    void ACCURACY_DESC_정렬은_keyword와_tags가_모두_없으면_예외가_발생한다() {
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            em.flush();
            return owner.getId();
        });

        PostSearchRequest invalid = new PostSearchRequest(null, null, null, PostSortType.ACCURACY_DESC);

        assertThatThrownBy(() -> postService.search(ownerId, invalid, null, 20))
            .as("keyword도 tags도 없이 정확도순 정렬을 요청하면 쿼리까지 가지 않고 여기서 막혀야 한다")
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void ACCURACY_DESC_정렬은_tags만_있어도_태그_일치_개수가_많은_게시물이_먼저_나온다() {
        UUID[] result = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post low = persistPost(owner, "[]", VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 9, 1), 0, List.of(TagType.TRAVEL));
            Post high = persistPost(owner, "[]", VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 9, 1), 0, List.of(TagType.TRAVEL, TagType.FOOD));
            em.flush();
            return new UUID[]{owner.getId(), high.getId(), low.getId()};
        });
        UUID ownerId = result[0];
        UUID highId = result[1];
        UUID lowId = result[2];

        // tags가 ACCURACY_DESC에서는 OR 매칭이라 다른 테스트가 커밋한 TRAVEL/FOOD 게시물도
        // 결과에 섞일 수 있다 (author 스코프가 없는 설계 + 롤백 없는 테스트 DB 특성상 불가피).
        // 그래서 전체 결과에서 이 두 게시물만 걸러내 그 둘 사이의 상대 순서만 검증한다.
        PostSearchRequest condition = new PostSearchRequest(
            null, List.of(TagType.TRAVEL, TagType.FOOD), null, PostSortType.ACCURACY_DESC);

        PostListResponse page = postService.search(ownerId, condition, null, 1000);

        List<UUID> relevantOrder = page.items().stream()
            .map(PostSummaryResponse::postId)
            .filter(id -> id.equals(highId) || id.equals(lowId))
            .toList();

        assertThat(relevantOrder)
            .as("둘 다 결과에 있어야 하고, 태그를 더 많이 겹치는 게시물(2개)이 적게 겹치는 게시물(1개)보다 먼저 나와야 한다")
            .containsExactly(highId, lowId);
    }

    @Test
    void sort가_LATEST면_recordedDate_내림차순으로_정렬된다() {
        String marker = uniqueMarker();
        UUID[] result = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post older = persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 8, 1), 0, List.of());
            Post newer = persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 8, 10), 0, List.of());
            em.flush();
            return new UUID[]{owner.getId(), newer.getId(), older.getId()};
        });
        UUID ownerId = result[0];
        UUID newerId = result[1];
        UUID olderId = result[2];

        PostSearchRequest condition = new PostSearchRequest(marker, null, null, PostSortType.LATEST);

        PostListResponse page = postService.search(ownerId, condition, null, 20);

        assertThat(page.items().get(0).postId()).isEqualTo(newerId);
        assertThat(page.items().get(1).postId()).isEqualTo(olderId);
    }

    @Test
    void sort가_VIEW_COUNT_DESC면_조회수_내림차순으로_정렬된다() {
        String marker = uniqueMarker();
        UUID[] result = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post fewViews = persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 8, 20), 2, List.of());
            Post manyViews = persistPost(owner, contentWith(marker), VisibilityType.PUBLIC, TimeslotType.AM,
                LocalDate.of(2030, 8, 20), 9, List.of());
            em.flush();
            return new UUID[]{owner.getId(), manyViews.getId(), fewViews.getId()};
        });
        UUID ownerId = result[0];
        UUID manyId = result[1];
        UUID fewId = result[2];

        PostSearchRequest condition = new PostSearchRequest(marker, null, null, PostSortType.VIEW_COUNT_DESC);

        PostListResponse page = postService.search(ownerId, condition, null, 20);

        assertThat(page.items().get(0).postId()).isEqualTo(manyId);
        assertThat(page.items().get(1).postId()).isEqualTo(fewId);
    }

    @Test
    void FRIENDS_공개_게시물은_양방향_ACCEPTED_팔로우가_있으면_검색결과에_나온다() {
        String marker = uniqueMarker();
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User friend = persistUser("friend" + UUID.randomUUID().toString().substring(0, 6));
            persistAcceptedFollow(friend, owner); // friend가 owner를 팔로우, ACCEPTED
            persistPost(owner, contentWith(marker), VisibilityType.FRIENDS, TimeslotType.AM,
                LocalDate.of(2030, 10, 1), 0, List.of());
            em.flush();
            return new UUID[]{owner.getId(), friend.getId()};
        });
        UUID friendId = ids[1];

        PostSearchRequest condition = new PostSearchRequest(marker, null, null, null);

        PostListResponse result = postService.search(friendId, condition, null, 20);

        assertThat(result.items())
            .as("양방향 ACCEPTED 팔로우 관계면 FRIENDS 공개 글도 검색에 나와야 한다 (#141)")
            .hasSize(1);
    }

    @Test
    void FRIENDS_공개_게시물은_팔로우_관계가_없으면_검색결과에_나오지_않는다() {
        String marker = uniqueMarker();
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User stranger = persistUser("stranger" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, contentWith(marker), VisibilityType.FRIENDS, TimeslotType.AM,
                LocalDate.of(2030, 10, 2), 0, List.of());
            em.flush();
            return new UUID[]{owner.getId(), stranger.getId()};
        });
        UUID strangerId = ids[1];

        PostSearchRequest condition = new PostSearchRequest(marker, null, null, null);

        PostListResponse result = postService.search(strangerId, condition, null, 20);

        assertThat(result.items())
            .as("팔로우 관계가 없으면 FRIENDS 공개 글은 검색에 나오면 안 된다")
            .isEmpty();
    }
}
