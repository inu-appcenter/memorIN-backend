package com.memorin.domain.posts;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostSummaryResponse;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.users.entity.User;
import com.memorin.global.exception.PostExceptions;
import com.memorin.support.PostgresTestSupport;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sprint 2 게이트 "공개범위(전체/친구/나만보기) 필터링 정확" 검증.
//
// 이 테스트가 지키는 핵심 불변식은 "목록과 단건의 판정이 같다"는 것이다.
// 목록(PostRepository.findUserFeed)은 SQL 안에 친구 조건을 갖고 있고,
// 단건(PostAccessPolicy.assertReadable)은 자바로 판정한다. 구현이 둘로 나뉘어 있어
// 한쪽만 고치면 "목록엔 보이는데 눌러 들어가면 403"이 조용히 생긴다.
// 개수만 세지 않고 마지막에 교차 검증까지 하는 이유다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostVisibilityFilterTest extends PostgresTestSupport {

    @Autowired
    private PostService postService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private UUID authorId;
    private UUID followerFriendId;  // 친구가 나(작성자)를 팔로우한 방향
    private UUID followingFriendId; // 내(작성자)가 친구를 팔로우한 방향
    private UUID strangerId;
    private UUID publicPostId;
    private UUID friendsPostId;
    private UUID privatePostId;

    @BeforeEach
    void setUp() {
        // 테스트마다 유니크 제약(email/username)에 걸리지 않도록 접미사를 붙인다.
        String tag = UUID.randomUUID().toString().substring(0, 8);

        tx.executeWithoutResult(status -> {
            User author = persistUser("author-" + tag);
            User followerFriend = persistUser("follower-" + tag);
            User followingFriend = persistUser("following-" + tag);
            User stranger = persistUser("stranger-" + tag);

            // 친구 판정은 양방향이다. 두 방향을 각각 심어 OR의 양쪽 가지를 모두 검증한다.
            em.persist(accepted(followerFriend, author)); // followerFriend -> author
            em.persist(accepted(author, followingFriend)); // author -> followingFriend

            // 팔로우는 걸려 있지만 PENDING이면 친구가 아니다.
            em.persist(new Follows(stranger, author));

            Post publicPost = persistPost(author, VisibilityType.PUBLIC, 1);
            Post friendsPost = persistPost(author, VisibilityType.FRIENDS, 2);
            Post privatePost = persistPost(author, VisibilityType.PRIVATE, 3);

            em.flush();

            authorId = author.getId();
            followerFriendId = followerFriend.getId();
            followingFriendId = followingFriend.getId();
            strangerId = stranger.getId();
            publicPostId = publicPost.getId();
            friendsPostId = friendsPost.getId();
            privatePostId = privatePost.getId();
        });
    }

    private User persistUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        em.persist(user);
        return user;
    }

    private Follows accepted(User follower, User following) {
        Follows follows = new Follows(follower, following);
        follows.accept();
        return follows;
    }

    private Post persistPost(User author, VisibilityType visibility, int dayOffset) {
        Post post = Post.create(author, "[]", visibility, TimeslotType.AM,
                Date.valueOf(LocalDate.of(2026, 8, 1).plusDays(dayOffset)));
        em.persist(post);
        return post;
    }

    private List<UUID> visiblePostIds(UUID requesterId) {
        PostListResponse response = postService.list(authorId, requesterId, null, 20);
        return response.items().stream()
                .map(PostSummaryResponse::postId)
                .map(UUID::fromString)
                .toList();
    }

    @Test
    void 본인은_나만보기까지_전부_본다() {
        assertThat(visiblePostIds(authorId))
                .containsExactlyInAnyOrder(publicPostId, friendsPostId, privatePostId);
    }

    @Test
    void 친구는_전체공개와_친구공개만_본다() {
        // 친구가 나를 팔로우한 방향
        assertThat(visiblePostIds(followerFriendId))
                .as("친구 -> 작성자 방향 ACCEPTED")
                .containsExactlyInAnyOrder(publicPostId, friendsPostId);

        // 내가 친구를 팔로우한 방향 (반대 방향도 친구로 인정돼야 한다)
        assertThat(visiblePostIds(followingFriendId))
                .as("작성자 -> 친구 방향 ACCEPTED")
                .containsExactlyInAnyOrder(publicPostId, friendsPostId);
    }

    @Test
    void 남남은_전체공개만_본다() {
        // stranger는 PENDING 상태라 친구가 아니다.
        assertThat(visiblePostIds(strangerId)).containsExactly(publicPostId);
    }

    @Test
    void 비로그인은_전체공개만_본다() {
        assertThat(visiblePostIds(null)).containsExactly(publicPostId);
    }

    @Test
    void 단건_조회도_같은_기준으로_막힌다() {
        // 친구는 FRIENDS까지 열리고 PRIVATE은 막힌다
        assertThatCode(() -> postService.getOne(friendsPostId, followerFriendId))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> postService.getOne(privatePostId, followerFriendId))
                .isInstanceOf(PostExceptions.PostAccessDeniedException.class);

        // 남남은 FRIENDS부터 막힌다
        assertThatThrownBy(() -> postService.getOne(friendsPostId, strangerId))
                .isInstanceOf(PostExceptions.PostAccessDeniedException.class);

        // 비로그인도 마찬가지
        assertThatThrownBy(() -> postService.getOne(friendsPostId, null))
                .isInstanceOf(PostExceptions.PostAccessDeniedException.class);
    }

    // 이 테스트가 이 파일의 핵심이다.
    // 목록에 나온 게시물은 단건 조회도 반드시 열려야 한다. 하나라도 403이면 판정이 갈라진 것이다.
    @Test
    void 목록에_보이는_게시물은_단건_조회도_열린다() {
        for (UUID requesterId : new UUID[]{authorId, followerFriendId, followingFriendId, strangerId, null}) {
            for (UUID postId : visiblePostIds(requesterId)) {
                assertThatCode(() -> postService.getOne(postId, requesterId))
                        .as("목록에는 나왔는데 단건은 막힌 게시물: post=%s, requester=%s", postId, requesterId)
                        .doesNotThrowAnyException();
            }
        }
    }
}
