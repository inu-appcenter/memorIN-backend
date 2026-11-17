package com.memorin.domain.follows;

import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.users.dto.UserFollowResponse;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.service.UserService;
import com.memorin.global.media.dto.response.PresignedDownloadResponse;
import com.memorin.global.media.service.PresignedDownloadService;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

// #347: 팔로워·팔로잉 목록이 프로필 이미지를 스토리지 키로만 내려줘서 화면에 그릴 수 없던 문제.
// 다른 프로필 응답처럼 presigned URL을 profileImageUrl로 함께 내려주는지 확인한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FollowListProfileImageTest extends PostgresTestSupport {

    @MockitoBean
    private PresignedDownloadService presignedDownloadService;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private record Seeded(UUID targetId, String imageKey) {}

    // target과 서로 팔로우(ACCEPTED)하는 상대 두 명: 한 명은 프로필 이미지가 있고 한 명은 없다.
    private Seeded seed() {
        return tx.execute(status -> {
            String tag = "p" + UUID.randomUUID().toString().substring(0, 8);
            User target = new User(tag + "-t@memorin.test", "hash", tag + "-t", tag + "-t", null);
            User withImage = new User(tag + "-a@memorin.test", "hash", tag + "-a", tag + "-a", null);
            User withoutImage = new User(tag + "-b@memorin.test", "hash", tag + "-b", tag + "-b", null);
            String imageKey = "profiles/" + tag + ".png";
            withImage.updateProfile(null, false, null, false, imageKey, true);
            em.persist(target);
            em.persist(withImage);
            em.persist(withoutImage);
            for (User other : List.of(withImage, withoutImage)) {
                Follows in = new Follows(other, target);
                in.accept();
                em.persist(in);
                Follows out = new Follows(target, other);
                out.accept();
                em.persist(out);
            }
            em.flush();
            return new Seeded(target.getId(), imageKey);
        });
    }

    private void assertProfileImages(List<UserFollowResponse> items, String imageKey) {
        assertThat(items).hasSize(2);
        UserFollowResponse withImage = items.stream()
                .filter(i -> imageKey.equals(i.profileImageKey())).findFirst().orElseThrow();
        UserFollowResponse withoutImage = items.stream()
                .filter(i -> i.profileImageKey() == null).findFirst().orElseThrow();
        assertThat(withImage.profileImageUrl()).isEqualTo("https://storage.test/" + imageKey);
        assertThat(withoutImage.profileImageUrl()).isNull();
    }

    @Test
    void 팔로워와_팔로잉_목록이_프로필_이미지_URL을_내려준다() {
        Seeded s = seed();
        given(presignedDownloadService.createDownloadUrl(anyString())).willAnswer(inv -> {
            String key = inv.getArgument(0);
            return new PresignedDownloadResponse("https://storage.test/" + key, key, Instant.now());
        });

        assertProfileImages(userService.getFollowers(s.targetId(), null, 10).items(), s.imageKey());
        assertProfileImages(userService.getFollowings(s.targetId(), null, 10).items(), s.imageKey());
    }

    @Test
    void URL_발급에_실패해도_목록은_내려가고_URL만_null이다() {
        Seeded s = seed();
        given(presignedDownloadService.createDownloadUrl(anyString()))
                .willThrow(new IllegalStateException("storage down"));

        List<UserFollowResponse> items = userService.getFollowers(s.targetId(), null, 10).items();

        assertThat(items).hasSize(2);
        assertThat(items).extracting(UserFollowResponse::profileImageUrl).containsOnlyNulls();
    }
}
