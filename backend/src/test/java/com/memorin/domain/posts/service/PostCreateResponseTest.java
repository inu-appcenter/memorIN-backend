package com.memorin.domain.posts.service;

import com.memorin.domain.posts.dto.request.PostCreateRequest;
import com.memorin.domain.posts.dto.response.PostCreateResponse;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 게시물 생성 응답의 createdAt이 null로 오던 문제를 검증한다.
// save()만 호출하면 INSERT가 트랜잭션 끝까지 지연돼 @CreationTimestamp가 아직
// 값을 채우기 전 상태로 응답 DTO가 만들어졌다. DB에는 정상 저장되므로
// GET으로 다시 조회하면 값이 보여서 눈에 잘 안 띄는 버그였다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostCreateResponseTest extends PostgresTestSupport {

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 게시물_생성_응답에_createdAt이_채워져있다() {
        // given
        User author = userRepository.save(new User(
                "post-create-response-test@memorin.dev", "hash",
                "post_create_response_test", "Post Create Response Test", null
        ));
        PostCreateRequest request = new PostCreateRequest(
                "[{\"type\":\"text\",\"value\":\"테스트 기록\"}]",
                VisibilityType.PUBLIC,
                TimeslotType.AM,
                LocalDate.now(),
                List.of()
        );

        // when
        PostCreateResponse response = postService.create(author.getId(), request);

        // then
        assertThat(response.createdAt()).isNotNull();
    }
}
