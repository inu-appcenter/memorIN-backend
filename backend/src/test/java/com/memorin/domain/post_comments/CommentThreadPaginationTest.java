package com.memorin.domain.post_comments;

import com.memorin.domain.post_comments.dto.response.PostCommentPageResponse;
import com.memorin.domain.post_comments.dto.response.PostCommentResponse;
import com.memorin.domain.post_comments.entity.PostComments;
import com.memorin.domain.post_comments.service.PostCommentService;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 댓글 스레드 커서 페이징이 실제로 동작하는지 — 페이지를 끝까지 넘겨본다.
//
// 쿼리 카운트 테스트(CommentThreadQueryCountTest)와 역할이 다르다. 그쪽은 SQL "개수"만 세므로
// 커서를 통째로 무시하고 매번 전체를 반환해도 통과한다(docs/n+1-audit.md §6-4).
// 페이징이 진짜 되는지는 이렇게 넘겨봐야 안다.
//
// 이 파일이 지키는 계약 셋:
//   1. 끝까지 넘기면 모든 댓글이 정확히 한 번씩 나온다 (누락도 중복도 없다)
//   2. 대댓글은 항상 부모와 같은 페이지에 있다 — 페이지 경계로 갈라지면 FE가 트리를 못 그린다
//   3. size가 세는 것은 최상위 댓글 수다. 대댓글 때문에 items는 그보다 클 수 있다
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommentThreadPaginationTest extends PostgresTestSupport {

    @Autowired
    private PostCommentService postCommentService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private UUID readerId;

    /** 최상위 댓글 rootCount개. 그중 짝수 번째에는 대댓글을 2개씩 단다. */
    private UUID seed(String tag, int rootCount) {
        return tx.execute(status -> {
            User author = persistUser(tag + "-author");
            readerId = persistUser(tag + "-reader").getId();

            Post post = Post.create(author, "[]", VisibilityType.PUBLIC, TimeslotType.AM,
                Date.valueOf(LocalDate.of(2026, 8, 1)), List.of(TagType.ETC));
            em.persist(post);

            LocalDateTime base = LocalDateTime.of(2026, 8, 1, 9, 0);
            for (int i = 0; i < rootCount; i++) {
                PostComments root = PostComments.of(post, author, null, "root-" + i, base.plusMinutes(i));
                em.persist(root);
                em.flush();   // 부모 id가 먼저 확정돼야 대댓글이 그 뒤 id를 받는다

                if (i % 2 == 0) {
                    for (int r = 0; r < 2; r++) {
                        em.persist(PostComments.of(post, author, root,
                            "reply-%d-%d".formatted(i, r), base.plusMinutes(i).plusSeconds(r + 1)));
                    }
                }
            }
            em.flush();
            return post.getId();
        });
    }

    private User persistUser(String tag) {
        String unique = tag + UUID.randomUUID().toString().substring(0, 6);
        User user = new User(unique + "@memorin.test", "hash", unique, unique, null);
        em.persist(user);
        return user;
    }

    @Test
    void 페이지를_끝까지_넘기면_모든_댓글이_정확히_한_번씩_나온다() {
        int rootCount = 7;                    // 대댓글은 짝수 번째(0·2·4·6)에 2개씩 = 8개
        int expectedTotal = rootCount + 8;
        UUID postId = seed("page" + UUID.randomUUID().toString().substring(0, 6), rootCount);

        List<String> collected = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        UUID cursor = null;
        int guard = 0;

        while (true) {
            PostCommentPageResponse page = postCommentService.getThread(postId, readerId, cursor, 2);
            pageSizes.add(page.items().size());
            page.items().forEach(c -> collected.add(c.body()));

            // 계약 2 — 대댓글은 부모와 같은 페이지에 있다.
            List<String> idsInPage = page.items().stream().map(PostCommentResponse::commentId).toList();
            assertThat(page.items())
                .filteredOn(c -> c.parentId() != null)
                .allSatisfy(reply -> assertThat(idsInPage)
                    .as("대댓글 %s의 부모가 같은 페이지에 없다", reply.body())
                    .contains(reply.parentId()));

            if (!page.hasNext()) {
                assertThat(page.nextCursor()).as("마지막 페이지의 커서는 null이어야 한다").isNull();
                break;
            }
            assertThat(page.nextCursor()).as("hasNext면 커서가 있어야 한다").isNotNull();
            cursor = page.nextCursor();

            if (++guard > 20) {
                throw new IllegalStateException("페이지네이션이 끝나지 않는다 — 무한 루프 방지용 가드에 걸림");
            }
        }

        System.out.printf("%n>>> 최상위 %d개(+대댓글 8개) → 페이지 %d개, 페이지별 항목 수 %s%n%n",
            rootCount, pageSizes.size(), pageSizes);

        // 계약 1 — 누락도 중복도 없다.
        assertThat(collected).as("전체 댓글 수").hasSize(expectedTotal);
        assertThat(collected).doesNotHaveDuplicates();

        // 계약 3 — size는 최상위 댓글 수를 센다. 대댓글이 있는 페이지는 항목이 더 많다.
        assertThat(pageSizes).as("size=2인데 대댓글 덕분에 2보다 큰 페이지가 있어야 한다")
            .anyMatch(n -> n > 2);
    }

    @Test
    void 댓글이_없으면_빈_페이지를_돌려준다() {
        UUID postId = seed("empty" + UUID.randomUUID().toString().substring(0, 6), 0);

        PostCommentPageResponse page = postCommentService.getThread(postId, readerId, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    // size를 주지 않으면 기본값으로, 과하게 크게 주면 상한으로 잘린다.
    // 상한이 없으면 ?size=1000000이 그대로 통한다(docs/n+1-audit.md §6-4).
    @Test
    void size는_기본값과_상한이_적용된다() {
        UUID postId = seed("size" + UUID.randomUUID().toString().substring(0, 6), 3);

        assertThat(postCommentService.getThread(postId, readerId, null, null).hasNext())
            .as("기본 size(20)면 최상위 3개는 한 페이지에 들어간다")
            .isFalse();

        assertThat(postCommentService.getThread(postId, readerId, null, 1_000_000).items())
            .as("상한을 넘겨도 예외 없이 처리된다")
            .isNotEmpty();

        assertThat(postCommentService.getThread(postId, readerId, null, 0).items())
            .as("0 이하는 최소 1로 보정된다")
            .isNotEmpty();
    }
}
