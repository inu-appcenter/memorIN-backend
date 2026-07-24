package com.memorin.domain.follows.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.follows.dto.FollowRequest;
import com.memorin.domain.follows.service.FollowService;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// FollowController가 인증된 사용자를 UserDetailsImpl(JwtAuthenticationFilter가 실제로 넣는 타입)로
// 올바르게 꺼내는지 검증한다. 예전엔 @AuthenticationPrincipal User로 받아 타입이 안 맞아
// null이 주입되고 매 요청이 NullPointerException으로 500이 났었다.
@WebMvcTest(FollowController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FollowService followService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl userDetails = org.mockito.Mockito.mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        given(userDetails.getAuthorities()).willReturn(null);
        return userDetails;
    }

    @Test
    void request_인증된_사용자_id로_팔로우_요청을_처리한다() throws Exception {
        // given
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();

        // when
        // then
        mockMvc.perform(post("/api/follows")
                        .with(user(principalOf(followerId)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FollowRequest(followingId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(followService).request(followerId, followingId);
    }

    @Test
    void accept_인증된_사용자_id로_팔로우_수락을_처리한다() throws Exception {
        // given
        UUID followId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when
        // then
        mockMvc.perform(patch("/api/follows/{followId}/accept", followId)
                        .with(user(principalOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(followService).accept(followId, userId);
    }

    @Test
    void reject_인증된_사용자_id로_언팔로우를_처리한다() throws Exception {
        // given
        UUID followingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // when
        // then
        mockMvc.perform(delete("/api/follows/{followingId}", followingId)
                        .with(user(principalOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(followService).reject(userId, followingId);
    }

    @Test
    void request_인증이_없으면_401을_반환한다() throws Exception {
        // when
        // then
        mockMvc.perform(post("/api/follows")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FollowRequest(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }
}
