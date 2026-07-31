package com.memorin.domain.posts;


import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.service.RecommendedFeedService;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecommendedFeedQueryTest extends PostgresTestSupport {

    @Autowired
    private RecommendedFeedService recommendedFeedService;
    @Autowired private TransactionTemplate tx;
    @PersistenceContext
    private EntityManager em;

    @Test
    void 추천_피드_조회가_예외없이_동작하고_공개글을_돌려준다() {
        tx.execute(s -> {
            com.memorin.domain.users.entity.User author = new com.memorin.domain.users.entity.User("reco@memorin.test", "hash", "reco", "reco", null);
            em.persist(author);
            em.persist(Post.create(author, "[]", com.memorin.domain.posts.entity.VisibilityType.PUBLIC,
                com.memorin.domain.posts.entity.TimeslotType.AM, Date.valueOf(LocalDate.now())));
            em.flush();
            return null;
        });
        com.memorin.domain.posts.dto.response.PostListResponse response = recommendedFeedService.getRecommendedFeed(null, 5);
        assertThat(response.items()).isNotEmpty();
    }
}
