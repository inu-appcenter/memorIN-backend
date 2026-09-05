package com.memorin.domain.posts;

import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostSummaryResponse;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.users.entity.User;
import com.memorin.global.exception.BusinessException;
import com.memorin.support.PostgresTestSupport;
import com.memorin.domain.posts.entity.TagType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
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

// Sprint 2 게이트 "캘린더 뷰: 날짜 탭 시 해당 날 게시물 조회" 검증.
//
// 전체 피드를 받아 클라이언트에서 거르는 방식은 커서 페이지네이션과 충돌한다.
// 탭한 날짜의 게시물이 뒤쪽 페이지에 있으면 화면에 아예 나타나지 않기 때문이다.
// 그래서 서버가 recorded_date 범위로 좁힌다. 마지막 테스트가 이 둘의 공존을 고정한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostDateRangeFilterTest extends PostgresTestSupport {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 8, 1);

    @Autowired
    private PostService postService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private UUID authorId;

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString().substring(0, 8);

        tx.executeWithoutResult(status -> {
            User author = new User(tag + "@memorin.test", "hash", tag, tag, null);
            em.persist(author);

            // 8/1 ~ 8/10, 하루에 한 개씩
            for (int i = 0; i < 10; i++) {
                em.persist(Post.create(author, "[]", VisibilityType.PUBLIC,
                    TimeslotType.AM, Date.valueOf(DAY_1.plusDays(i)), List.of(TagType.ETC)));
            }
            em.flush();
            authorId = author.getId();
        });
    }

    private List<LocalDate> datesOf(LocalDate from, LocalDate to) {
        PostListResponse response = postService.list(authorId, authorId, null, 20, from, to);
        return response.items().stream().map(PostSummaryResponse::recordedDate).toList();
    }

    @Test
    void 범위를_주지_않으면_전체가_나온다() {
        assertThat(datesOf(null, null)).hasSize(10);
    }

    @Test
    void 날짜_하나를_탭하면_그_날짜만_나온다() {
        // 캘린더에서 8/4를 탭한 상황 — from == to
        assertThat(datesOf(DAY_1.plusDays(3), DAY_1.plusDays(3)))
                .containsExactly(DAY_1.plusDays(3));
    }

    @Test
    void 기간을_주면_경계를_포함해_그_안만_나온다() {
        // 8/3 ~ 8/5 (캘린더 월 이동에 쓰는 범위 조회)
        assertThat(datesOf(DAY_1.plusDays(2), DAY_1.plusDays(4)))
                .containsExactly(DAY_1.plusDays(4), DAY_1.plusDays(3), DAY_1.plusDays(2)); // recorded_date DESC
    }

    @Test
    void 한쪽만_줘도_동작한다() {
        assertThat(datesOf(DAY_1.plusDays(7), null)).hasSize(3); // 8/8, 8/9, 8/10
        assertThat(datesOf(null, DAY_1.plusDays(2))).hasSize(3); // 8/1, 8/2, 8/3
    }

    @Test
    void 범위에_게시물이_없으면_빈_목록이다() {
        assertThat(datesOf(DAY_1.plusMonths(1), DAY_1.plusMonths(2))).isEmpty();
    }

    @Test
    void from이_to보다_늦으면_400이다() {
        assertThatThrownBy(() -> postService.list(authorId, authorId, null, 20,
                DAY_1.plusDays(5), DAY_1))
                .isInstanceOf(BusinessException.class);
    }

    // 범위 필터와 커서 페이지네이션이 함께 동작해야 한다.
    // 범위가 커서 이동 중에 풀려버리면 다음 페이지에 범위 밖 게시물이 섞여 들어온다.
    @Test
    void 범위_안에서_커서로_끝까지_넘겨도_범위가_유지된다() {
        LocalDate from = DAY_1.plusDays(2); // 8/3
        LocalDate to = DAY_1.plusDays(8);   // 8/9 → 7개

        List<LocalDate> collected = new ArrayList<>();
        String cursor = null;
        int guard = 0;

        do {
            PostListResponse page = postService.list(authorId, authorId, cursor, 3, from, to);
            page.items().stream().map(PostSummaryResponse::recordedDate).forEach(collected::add);
            cursor = page.nextCursor();

            if (++guard > 10) throw new IllegalStateException("페이지네이션이 끝나지 않는다");
        } while (cursor != null);

        assertThat(collected)
                .as("페이지를 넘겨도 범위 밖 게시물이 섞이면 안 된다")
                .hasSize(7)
                .allSatisfy(d -> assertThat(d).isBetween(from, to))
                .doesNotHaveDuplicates();
    }
}
