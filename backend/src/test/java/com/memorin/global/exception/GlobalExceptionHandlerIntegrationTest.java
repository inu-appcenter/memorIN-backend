package com.memorin.global.exception;

import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// 등록되지 않은 경로, 등록됐지만 메서드가 안 맞는 경로가 각각 404/405로 응답하는지
// 실제 내장 서버(RANDOM_PORT)로 검증한다. @WebMvcTest의 MockMvc는 DispatcherServlet의
// 라우팅 실패 처리를 운영과 똑같이 재현하지 못해서, 실제 서버가 필요하다.
// 이 핸들러가 없으면 두 경우 모두 GlobalExceptionHandler의 Exception.class에 걸려
// 500 COMMON_001로 응답했다 — 클라이언트 실수와 서버 내부 오류가 구분되지 않았다.
//
// 인증 필터가 라우팅보다 먼저 동작하므로(인증 안 된 요청은 404/405 판정 전에 401로
// 막힌다), 유효한 토큰으로 호출해야 이 문제가 재현된다. 로그인 엔드포인트를 거치지
// 않고 JwtTokenProvider로 토큰을 직접 발급해, 이 테스트를 로그인 관련 이슈와
// 무관하게 독립적으로 유지한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GlobalExceptionHandlerIntegrationTest extends PostgresTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private HttpHeaders authHeaders() {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        User user = new User(
                "exception-handler-test-" + suffix + "@memorin.dev", "hash",
                "exception_handler_test_" + suffix, "Exception Handler Test", null
        );
        userRepository.save(user);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void 등록되지_않은_경로는_500이_아니라_404를_반환한다() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/totally-unmapped-path", HttpMethod.GET, request, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("COMMON_004");
    }

    @Test
    void 등록된_경로에_잘못된_메서드로_호출하면_500이_아니라_405를_반환한다() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/posts", HttpMethod.DELETE, request, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).contains("COMMON_005");
    }
}
