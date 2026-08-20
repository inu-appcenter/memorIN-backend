package com.memorin.domain.posts;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.posts.controller.PostController;
import com.memorin.domain.posts.service.PostCursor;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.posts.service.RecommendedFeedService;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.exception.PostExceptions;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 게시물 도메인 예외가 전역 응답 포맷과 올바른 상태코드로 내려가는지 검증한다.
// PostExceptions가 BusinessException이 아니면 전부 500(COMMON_001)으로 떨어진다.
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class PostErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    // 컨트롤러가 주입받는 협력자라 슬라이스 테스트에도 필요하다. 이 테스트에서 호출하지는 않는다.
    @MockitoBean
    private RecommendedFeedService recommendedFeedService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl userDetails = org.mockito.Mockito.mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        given(userDetails.getAuthorities()).willReturn(null);
        return userDetails;
    }

    @Test
    void 존재하지_않는_게시물은_404와_POST_001을_반환한다() throws Exception {
        UUID postId = UUID.randomUUID();
        given(postService.getOne(any(), any()))
                .willThrow(new PostExceptions.PostNotFoundException(postId.toString()));

        mockMvc.perform(get("/api/posts/{postId}", postId)
                        .with(user(principalOf(UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POST_001"));
    }

    @Test
    void 접근_권한이_없으면_403과_POST_002를_반환한다() throws Exception {
        given(postService.getOne(any(), any()))
                .willThrow(new PostExceptions.PostAccessDeniedException());

        mockMvc.perform(get("/api/posts/{postId}", UUID.randomUUID())
                        .with(user(principalOf(UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POST_002"));
    }

    @Test
    void 잘못된_커서는_400과_POST_003을_반환한다() throws Exception {
        // 컨트롤러는 날짜 범위(from/to)까지 받는 6인자 오버로드를 호출한다.
        given(postService.list(any(), any(), any(), any(), any(), any()))
                .willThrow(new PostExceptions.InvalidCursorException(new RuntimeException("broken")));

        mockMvc.perform(get("/api/posts")
                        .param("cursor", "!!!not-base64!!!")
                        .with(user(principalOf(UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POST_003"));
    }

    @Test
    void PostCursor는_깨진_값을_받으면_InvalidCursorException을_던진다() {
        assertThatThrownBy(() -> PostCursor.decode("!!!not-base64!!!"))
                .isInstanceOf(PostExceptions.InvalidCursorException.class);
    }

    // content는 jsonb 컬럼에 매핑되므로, 유효한 JSON이 아니면 DB 저장 단계 500이 아니라
    // @ValidJson 검증에서 400(COMMON_002)으로 걸러져야 한다. (이슈 #77)
    @Test
    void 게시글_content가_유효한_JSON이_아니면_400과_COMMON_002를_반환한다() throws Exception {
        String body = """
                {"content":"평문 텍스트","visibilityType":"PUBLIC","timeslotType":"AM","attachments":[]}
                """;

        mockMvc.perform(post("/api/posts")
                        .with(user(principalOf(UUID.randomUUID())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_002"));
    }
}
