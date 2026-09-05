package com.memorin.domain.posts;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostResponse;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.posts.repository.PostRepository;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.exception.PostExceptions;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostFeedIntegrationTest extends PostgresTestSupport {

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    EntityManager em;

    @Test
    @Transactional
    void 공개글은_다른사용자가_조회할수있다() {

        User writer = new User(
            "writer@test.com",
            "pw",
            "writer",
            "writer",
            null
        );

        User reader = new User(
            "reader@test.com",
            "pw",
            "reader",
            "reader",
            null
        );

        em.persist(writer);
        em.persist(reader);

        Post post = Post.create(
            writer,
            "{}",
            VisibilityType.PUBLIC,
            TimeslotType.AM,
            Date.valueOf(LocalDate.now()),
            List.of(com.memorin.domain.posts.entity.TagType.ETC)
        );

        em.persist(post);
        em.flush();

        PostResponse response = postService.getOne(post.getId(), reader.getId());
        assertThat(response.postId()).isEqualTo(post.getId().toString());
    }

    @Test
    @Transactional
    void 비공개글은_작성자가_아니면_조회불가() {

        User writer = new User(
            "writer2@test.com",
            "pw",
            "writer2",
            "writer2",
            null
        );

        User reader = new User(
            "reader2@test.com",
            "pw",
            "reader2",
            "reader2",
            null
        );

        em.persist(writer);
        em.persist(reader);

        Post post = Post.create(
            writer,
            "{}",
            VisibilityType.PRIVATE,
            TimeslotType.AM,
            Date.valueOf(LocalDate.now()),
            List.of(com.memorin.domain.posts.entity.TagType.ETC)
        );

        em.persist(post);

        assertThatThrownBy(() -> postService.getOne(post.getId(), reader.getId()))
            .isInstanceOf(PostExceptions.PostAccessDeniedException.class);
    }

    @Test
    @Transactional
    void 친구공개글은_팔로우하면_조회된다() {

        User writer = new User(
            "writer3@test.com",
            "pw",
            "writer3",
            "writer3",
            null
        );

        User follower = new User(
            "reader3@test.com",
            "pw",
            "reader3",
            "reader3",
            null
        );

        em.persist(writer);
        em.persist(follower);

        Follows follows = new Follows(follower, writer);
        follows.accept();

        em.persist(follows);

        Post post = Post.create(
            writer,
            "{}",
            VisibilityType.FRIENDS,
            TimeslotType.AM,
            Date.valueOf(LocalDate.now()),
            List.of(com.memorin.domain.posts.entity.TagType.ETC)
        );

        em.persist(post);

        PostResponse response = postService.getOne(post.getId(), follower.getId());
        assertThat(response.postId()).isEqualTo(post.getId().toString());
    }

    @Test
    @Transactional
    void 친구피드는_팔로우한사람의_게시글만_조회한다() {

        User me = new User(
            "me@test.com",
            "pw",
            "me",
            "me",
            null
        );

        User friend = new User(
            "friend@test.com",
            "pw",
            "friend",
            "friend",
            null
        );

        User stranger = new User(
            "other@test.com",
            "pw",
            "other",
            "other",
            null
        );

        em.persist(me);
        em.persist(friend);
        em.persist(stranger);

        Follows follows = new Follows(me, friend);
        follows.accept();

        em.persist(follows);

        Post friendPost = Post.create(
            friend,
            "{}",
            VisibilityType.PUBLIC,
            TimeslotType.AM,
            Date.valueOf(LocalDate.now()),
            List.of(com.memorin.domain.posts.entity.TagType.ETC)
        );

        Post strangerPost = Post.create(
            stranger,
            "{}",
            VisibilityType.PUBLIC,
            TimeslotType.AM,
            Date.valueOf(LocalDate.now()),
            List.of(com.memorin.domain.posts.entity.TagType.ETC)
        );

        em.persist(friendPost);
        em.persist(strangerPost);

        em.flush();

        PostListResponse response = postService.friendFeed(me.getId(), null, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).postId()).isEqualTo(friendPost.getId());
    }

    @Test
    @Transactional
    void 친구공개글은_팔로우하지않으면_조회할수없다() {

        User writer = new User(
            "writer4@test.com",
            "pw",
            "writer4",
            "writer4",
            null
        );

        User stranger = new User(
            "stranger@test.com",
            "pw",
            "stranger",
            "stranger",
            null
        );

        em.persist(writer);
        em.persist(stranger);

        Post post = Post.create(
            writer,
            "{}",
            VisibilityType.FRIENDS,
            TimeslotType.AM,
            Date.valueOf(LocalDate.now()),
            List.of(com.memorin.domain.posts.entity.TagType.ETC)
        );

        em.persist(post);
        em.flush();

        assertThatThrownBy(() -> postService.getOne(post.getId(), stranger.getId()))
            .isInstanceOf(PostExceptions.PostAccessDeniedException.class);
    }
}
