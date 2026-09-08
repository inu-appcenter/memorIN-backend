package com.memorin.domain.chat_rooms;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_rooms.controller.ChatRoomController;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.entity.Chat_type;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.domain.posts.controller.PostController;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.posts.service.RecommendedFeedService;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러가 "로그인한 사람이 누구인지"를 서비스로 제대로 넘기는지 검증한다.
//
// 왜 필요한가 — 기존 ChatRoomTest·PostSearchTest는 서비스를 직접 호출하고 UUID를 인자로
// 넘긴다. 컨트롤러를 지나가지 않으므로 @AuthenticationPrincipal 해석이 한 번도 실행되지 않고,
// principal 타입이 틀려도 CI가 green이다. 실제로 두 컨트롤러가 @AuthenticationPrincipal UUID로
// 받고 있었고(principal은 UserDetailsImpl이라 null이 주입됨) 그대로 릴리스됐다.
//
// 증상은 둘로 갈렸다.
//  - 채팅방: 서비스 진입부의 requesterId.equals(...)에서 NPE → 500
//  - 검색:   viewerId가 null이라 SQL의 "본인 글"·"친구 글" 조건이 UNKNOWN → PUBLIC만 반환.
//           에러가 나지 않아 결과만 조용히 틀렸다.
//
// 그래서 이 테스트는 상태코드가 아니라 "서비스에 도달한 UUID"를 직접 붙잡아 확인한다.
// 200이 떨어지는 것만 봐서는 검색 쪽 결함을 잡을 수 없다.
@WebMvcTest({ChatRoomController.class, PostController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChatRoomSearchAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private PostService postService;

    // PostController가 주입받는 협력자라 슬라이스 구성에 필요하다. 여기서 호출하지는 않는다.
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
    void 채팅방_생성은_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        given(chatRoomService.createDirectRoom(any(), any()))
            .willReturn(new ChatRoomResponse(UUID.randomUUID(), Chat_type.DIRECT, null, true));

        mockMvc.perform(post("/api/chat-rooms/direct")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + target + "\"}")
                .with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> requesterId = ArgumentCaptor.forClass(UUID.class);
        verify(chatRoomService).createDirectRoom(requesterId.capture(), any());

        assertThat(requesterId.getValue())
            .as("principal 타입이 틀리면 여기가 null이 된다 (서비스 진입부에서 NPE → 500)")
            .isEqualTo(me);
    }

    @Test
    void 내_채팅방_목록은_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        given(chatRoomService.listMyRooms(any())).willReturn(List.of());

        mockMvc.perform(get("/api/chat-rooms").with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> requesterId = ArgumentCaptor.forClass(UUID.class);
        verify(chatRoomService).listMyRooms(requesterId.capture());

        assertThat(requesterId.getValue()).isEqualTo(me);
    }

    @Test
    void 게시물_검색은_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        given(postService.search(any(), any(), any(), any()))
            .willReturn(new PostListResponse(List.of(), null, false));

        mockMvc.perform(get("/api/posts/search")
                .param("keyword", "여행")
                .with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> viewerId = ArgumentCaptor.forClass(UUID.class);
        verify(postService).search(viewerId.capture(), any(), any(), any());

        assertThat(viewerId.getValue())
            .as("null이면 SQL에서 본인 글·친구 글 조건이 UNKNOWN이 되어 PUBLIC만 나온다 — 에러 없이 결과만 틀린다")
            .isEqualTo(me);
    }
}
