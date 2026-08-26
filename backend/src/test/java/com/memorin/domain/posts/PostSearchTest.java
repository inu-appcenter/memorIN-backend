package com.memorin.domain.posts;

import com.memorin.domain.posts.dto.request.PostSearchRequest;
import com.memorin.domain.posts.dto.response.PostSummaryResponse;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 태그/메타데이터 검색 API의 "결과가 새거나 틀리면 안 되는" 경로를 검증한다.
//
// 주의: search()는 작성자로 범위를 좁히지 않는다 (PUBLIC 글은 누구에게나 보이는 게 정상 동작).
// 게다가 이 테스트들은 데이터를 롤백 없이 실제로 커밋한다 — 그래서 같은 클래스의 다른 테스트가
// 만든 PUBLIC 게시물이 그대로 DB에 남아 다음 테스트의 검색 결과에도 섞여 들어온다.
// 따라서 각 테스트는 자신만의 recordedDate(또는 범위)로 항상 검색을 스코프해서,
// "DB에 이 테스트 말고 아무것도 없다"는 가정 없이도 정확히 자기 데이터만 봐야 한다.
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

    private Post persistPost(User owner, VisibilityType visibility, TimeslotType timeslot,
                             LocalDate recordedDate, int viewCount, List<TagType> tags) {
        Post post = Post.create(owner, "[]", visibility, timeslot, Date.valueOf(recordedDate), tags);
        em.persist(post);
        for (int i = 0; i < viewCount; i++) {
            post.increaseViewCount();
        }
        return post;
    }

    @Test
    void 비공개_게시물은_소유자가_아니면_어떤_필터로도_검색결과에_나오지_않는다() {
        LocalDate testDate = LocalDate.of(2030, 1, 1); // 이 테스트 전용 날짜 — 다른 테스트와 절대 겹치지 않음
        UUID[] ids = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            User stranger = persistUser("stranger" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, VisibilityType.PRIVATE, TimeslotType.AM, testDate, 0, List.of(TagType.TRAVEL));
            em.flush();
            return new UUID[]{owner.getId(), stranger.getId()};
        });

        PostSearchRequest scoped = new PostSearchRequest(null, null, null, null, testDate, testDate);

        Page<PostSummaryResponse> asStranger = postService.search(ids[1], scoped, PageRequest.of(0, 20));
        Page<PostSummaryResponse> asOwner = postService.search(ids[0], scoped, PageRequest.of(0, 20));

        assertThat(asStranger.getContent())
            .as("타인의 비공개 게시물은 어떤 필터로도 보이면 안 된다")
            .isEmpty();
        assertThat(asOwner.getContent())
            .as("본인 게시물은 비공개여도 자신에게는 보여야 한다")
            .hasSize(1);
    }

    @Test
    void 삭제된_게시물은_공개글이어도_검색결과에_나오지_않는다() {
        LocalDate testDate = LocalDate.of(2030, 2, 1);
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            Post post = persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, testDate, 0, List.of(TagType.TRAVEL));
            post.softDelete();
            em.flush();
            return owner.getId();
        });

        PostSearchRequest scoped = new PostSearchRequest(null, null, null, null, testDate, testDate);

        Page<PostSummaryResponse> result = postService.search(ownerId, scoped, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void 태그_필터는_지정한_태그를_모두_가진_게시물만_AND로_매칭한다() {
        LocalDate testDate = LocalDate.of(2030, 3, 1);
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, testDate, 0, List.of(TagType.TRAVEL));
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, testDate, 0, List.of(TagType.TRAVEL, TagType.FOOD));
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, testDate, 0, List.of(TagType.FOOD));
            em.flush();
            return owner.getId();
        });

        PostSearchRequest condition = new PostSearchRequest(
            List.of(TagType.TRAVEL, TagType.FOOD), null, null, null, testDate, testDate);

        Page<PostSummaryResponse> result = postService.search(ownerId, condition, PageRequest.of(0, 20));

        assertThat(result.getContent())
            .as("여행과 음식을 '둘 다' 가진 게시물만 나와야 한다 (OR가 아니라 AND)")
            .hasSize(1);
        assertThat(result.getContent().get(0).tagTypes())
            .containsExactlyInAnyOrder(TagType.TRAVEL, TagType.FOOD);
    }

    @Test
    void viewCount와_recordedDate_범위_필터는_경계값을_포함한다() {
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, LocalDate.of(2030, 4, 1), 5, List.of());
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, LocalDate.of(2030, 4, 10), 10, List.of());
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, LocalDate.of(2030, 4, 20), 15, List.of());
            em.flush();
            return owner.getId();
        });

        PostSearchRequest range = new PostSearchRequest(
            null, null, 5, 10, LocalDate.of(2030, 4, 1), LocalDate.of(2030, 4, 10));

        Page<PostSummaryResponse> result = postService.search(ownerId, range, PageRequest.of(0, 20));

        assertThat(result.getContent())
            .as("min/max, from/to 경계값 자체는 포함(inclusive)돼야 한다")
            .hasSize(2);
    }

    @Test
    void 여러_필터는_모두_만족해야_결과에_포함된다() {
        LocalDate testDate = LocalDate.of(2030, 5, 1);
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.PM, testDate, 0, List.of(TagType.TRAVEL));
            persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, testDate, 0, List.of(TagType.TRAVEL));
            em.flush();
            return owner.getId();
        });

        PostSearchRequest condition = new PostSearchRequest(
            List.of(TagType.TRAVEL), TimeslotType.AM, null, null, testDate, testDate);

        Page<PostSummaryResponse> result = postService.search(ownerId, condition, PageRequest.of(0, 20));

        assertThat(result.getContent())
            .as("태그는 맞아도 timeslot이 안 맞으면 제외돼야 한다 (조건은 AND)")
            .hasSize(1);
    }

    @Test
    void 전체_개수와_실제_반환된_데이터_개수가_일치한다() {
        LocalDate testDate = LocalDate.of(2030, 6, 1);
        UUID ownerId = tx.execute(status -> {
            User owner = persistUser("owner" + UUID.randomUUID().toString().substring(0, 6));
            for (int i = 0; i < 5; i++) {
                persistPost(owner, VisibilityType.PUBLIC, TimeslotType.AM, testDate, 0, List.of(TagType.DAILY));
            }
            em.flush();
            return owner.getId();
        });

        PostSearchRequest scoped = new PostSearchRequest(null, null, null, null, testDate, testDate);

        Page<PostSummaryResponse> firstPage = postService.search(ownerId, scoped, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }
}
