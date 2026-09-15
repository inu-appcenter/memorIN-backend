package com.memorin.global.health.controller;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #230: DB 연결까지 확인하는 헬스체크. 공유 Testcontainers Postgres를 내렸다 올릴 순 없으니
// UP은 실제 DB로, DOWN은 DataSource를 목으로 대체해 검증한다.
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 인증_없이_호출해도_DB가_살아있으면_200과_UP을_반환한다() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(2)).willReturn(true);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void DB_커넥션을_못_얻으면_503과_DOWN을_반환한다() throws Exception {
        given(dataSource.getConnection()).willThrow(new SQLException("connection refused"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void 커넥션은_얻었지만_유효하지_않으면_503과_DOWN을_반환한다() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(2)).willReturn(false);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
