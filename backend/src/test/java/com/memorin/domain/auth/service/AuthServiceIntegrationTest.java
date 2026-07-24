package com.memorin.domain.auth.service;

import com.memorin.domain.auth.dto.LoginRequest;
import com.memorin.domain.auth.dto.LoginResponse;
import com.memorin.domain.auth.dto.SignupRequest;
import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// 회원가입 후 로그인이 실제 DB(운영과 동일한 01_init.sql로 만든 스키마)까지 왕복하는지 검증한다.
// 로그인은 매번 refresh_token 테이블에 저장을 시도하므로, 이 테이블이 마이그레이션에서
// 빠지면 이 테스트가 실패해 배포 전에 잡을 수 있다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthServiceIntegrationTest extends PostgresTestSupport {

    @Autowired
    private AuthService authService;

    @Test
    void 회원가입_후_로그인하면_accessToken과_refreshToken을_모두_받는다() {
        // given
        SignupRequest signupRequest = new SignupRequest(
                "auth-integration-test@memorin.dev",
                "Password123!",
                "auth_integration_test",
                "Auth Integration Test",
                null
        );
        authService.signup(signupRequest);

        // when
        LoginResponse response = authService.login(
                new LoginRequest("auth-integration-test@memorin.dev", "Password123!")
        );

        // then
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }
}
