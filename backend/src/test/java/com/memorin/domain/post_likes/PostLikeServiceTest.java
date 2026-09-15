package com.memorin.domain.post_likes;

import com.memorin.domain.post_likes.repository.PostLikeRepository;
import com.memorin.domain.post_likes.service.PostLikeService;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.PostExceptions;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// #182: post_likes 복구 검증. 실제 잠금/유니크 제약을 확인해야 하므로 Mockito가 아니라
// 진짜 Postgres(PostgresTestSupport)를 쓴다.
@SpringBootTest
class PostLikeServiceTest extends PostgresTestSupport {

    @Autowired
    private PostLikeService postLikeService;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private TransactionTemplate tx;
    @PersistenceContext
    private EntityManager em;

    private User seedUser(String suffix) {
        return tx.execute(status -> {
            User user = new User(suffix + "@memorin.test", "hash", suffix, suffix, null);
            em.persist(user);
            em.flush();
            return user;
        });
    }

    private Post seedPost(User author, VisibilityType visibility) {
        return tx.execute(status -> {
            Post post = Post.create(author, "[]", visibility, TimeslotType.AM, Date.valueOf(LocalDate.now()));
            em.persist(post);
            em.flush();
            return post;
        });
    }

    @Test
    void 처음_누르면_좋아요가_등록된다() {
        User author = seedUser("like-author-1");
        User liker = seedUser("like-user-1");
        Post post = seedPost(author, VisibilityType.PUBLIC);

        boolean liked = postLikeService.toggleLike(post.getId(), liker.getId());

        assertThat(liked).isTrue();
        assertThat(postLikeService.countLikes(post.getId())).isEqualTo(1);
        assertThat(postLikeService.isLikedBy(post.getId(), liker.getId())).isTrue();
    }

    @Test
    void 다시_누르면_좋아요가_취소된다() {
        User author = seedUser("like-author-2");
        User liker = seedUser("like-user-2");
        Post post = seedPost(author, VisibilityType.PUBLIC);

        postLikeService.toggleLike(post.getId(), liker.getId());
        boolean likedAfterSecondTap = postLikeService.toggleLike(post.getId(), liker.getId());

        assertThat(likedAfterSecondTap).isFalse();
        assertThat(postLikeService.countLikes(post.getId())).isEqualTo(0);
    }

    @Test
    void 존재하지_않는_게시물은_POST_001을_던진다() {
        User liker = seedUser("like-user-3");
        UUID missingPostId = UUID.randomUUID();

        assertThatThrownBy(() -> postLikeService.toggleLike(missingPostId, liker.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_001);
    }

    @Test
    void 비공개_게시물은_작성자가_아니면_접근이_차단된다() {
        User author = seedUser("like-author-4");
        User stranger = seedUser("like-user-4");
        Post post = seedPost(author, VisibilityType.PRIVATE);

        assertThatThrownBy(() -> postLikeService.toggleLike(post.getId(), stranger.getId()))
                .isInstanceOf(PostExceptions.PostAccessDeniedException.class);

        assertThatThrownBy(() -> postLikeService.assertReadableForLikes(post.getId(), stranger.getId()))
                .isInstanceOf(PostExceptions.PostAccessDeniedException.class);
    }

    @Test
    void 비공개_게시물이어도_작성자_본인은_좋아요를_누를_수_있다() {
        User author = seedUser("like-author-5");
        Post post = seedPost(author, VisibilityType.PRIVATE);

        boolean liked = postLikeService.toggleLike(post.getId(), author.getId());

        assertThat(liked).isTrue();
    }

    @Test
    void 여러_명이_누르면_좋아요_수가_그만큼_집계된다() {
        User author = seedUser("like-author-6");
        Post post = seedPost(author, VisibilityType.PUBLIC);

        for (int i = 0; i < 3; i++) {
            User liker = seedUser("like-user-6-" + i);
            postLikeService.toggleLike(post.getId(), liker.getId());
        }

        assertThat(postLikeService.countLikes(post.getId())).isEqualTo(3);
    }

    // 같은 사용자가 같은 게시물에 동시에 두 번 처음 누르는 경쟁 상태.
    // 잠금 없이 exists-체크 후 insert했다면 uq_post_like 위반으로 예외가 났을 것이다.
    @Test
    void 동시_더블탭은_좋아요_하나만_남긴다() throws Exception {
        User author = seedUser("like-author-7");
        User liker = seedUser("like-user-7");
        Post post = seedPost(author, VisibilityType.PUBLIC);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Boolean>> futures = List.of(
                    pool.submit(() -> attemptToggle(post.getId(), liker.getId(), ready, go)),
                    pool.submit(() -> attemptToggle(post.getId(), liker.getId(), ready, go))
            );

            ready.await(5, TimeUnit.SECONDS);
            go.countDown();

            for (Future<Boolean> f : futures) {
                assertThat(f.get(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(postLikeRepository.countByPostId(post.getId())).isEqualTo(1);
    }

    private boolean attemptToggle(UUID postId, UUID userId, CountDownLatch ready, CountDownLatch go) {
        try {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            return postLikeService.toggleLike(postId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
