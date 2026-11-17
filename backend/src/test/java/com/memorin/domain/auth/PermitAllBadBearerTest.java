package com.memorin.domain.auth;

import com.memorin.domain.auth.controller.AuthController;
import com.memorin.domain.auth.dto.LoginResponse;
import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.auth.service.AuthService;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.health.controller.HealthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #309: permitAll 경로는 만료되거나 잘못된 Bearer가 붙어 있어도 통과해야 한다.
 *
 * <p>필터는 목이 아닌 실제 {@link JwtTokenProvider}로 검증한다. 목으로 두면 "검증하면 예외"를
 * 직접 꾸며야 해서, 필터가 검증을 건너뛰는지를 실제 서명·만료 검사로 확인할 수 없다.
 */
@WebMvcTest({AuthController.class, HealthController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
    RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
    PermitAllBadBearerTest.RealJwtConfig.class})
class PermitAllBadBearerTest {

    private static final String SECRET = "permit-all-test-secret-must-be-at-least-32-bytes";
    private static final String GARBAGE = "Bearer garbage";

    @TestConfiguration
    static class RealJwtConfig {
        @Bean
        JwtTokenProvider jwtTokenProvider(UserRepository userRepository) {
            return new JwtTokenProvider(SECRET, 60_000, 60_000, userRepository);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private BuildProperties buildProperties;

    @BeforeEach
    void setUp() throws Exception {
        given(buildProperties.getVersion()).willReturn("test");
        Connection connection = mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(2)).willReturn(true);
        given(authService.login(any())).willReturn(new LoginResponse("a", "r"));
        given(authService.reissue(anyString())).willReturn(new LoginResponse("a2", "r2"));
    }

    /** 같은 키로 서명했지만 이미 만료된 access 토큰. 클라이언트가 refresh 직전에 들고 있는 그 토큰이다. */
    private String expiredAccessToken() {
        return "Bearer " + new JwtTokenProvider(SECRET, -1_000, 60_000, userRepository)
            .createAccessToken(UUID.randomUUID());
    }

    @Test
    void 잘못된_Bearer가_붙어도_refresh는_재발급한다() throws Exception {
        mockMvc.perform(post("/auth/refresh").header("Authorization", GARBAGE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"r\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("a2"));
    }

    @Test
    void 만료된_access_토큰이_붙어도_refresh는_재발급한다() throws Exception {
        mockMvc.perform(post("/auth/refresh").header("Authorization", expiredAccessToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"r\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("a2"));
    }

    @Test
    void 잘못된_Bearer가_붙어도_login은_통과한다() throws Exception {
        mockMvc.perform(post("/auth/login").header("Authorization", GARBAGE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.c\",\"password\":\"pw\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("a"));
    }

    @Test
    void 잘못된_Bearer가_붙어도_health는_200이다() throws Exception {
        mockMvc.perform(get("/api/health").header("Authorization", GARBAGE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // 회귀 방지: 인증이 필요한 /auth/logout까지 건너뛰면 안 된다. 만료 토큰은 계속 AUTH_003으로 알려야
    // 클라이언트가 refresh로 넘어간다.
    @Test
    void logout은_여전히_토큰을_검증한다() throws Exception {
        mockMvc.perform(delete("/auth/logout").header("Authorization", GARBAGE))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(delete("/auth/logout").header("Authorization", expiredAccessToken()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }
}
