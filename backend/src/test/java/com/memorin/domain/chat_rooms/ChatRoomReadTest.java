package com.memorin.domain.chat_rooms;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_rooms.controller.ChatRoomController;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #215: POST /api/chat-rooms/{roomId}/read 컨트롤러 레벨 검증.
@WebMvcTest(ChatRoomController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChatRoomReadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl userDetails = org.mockito.Mockito.mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        given(userDetails.getAuthorities()).willReturn(null);
        return userDetails;
    }

    @Test
    void 읽음_처리는_ApiResponse_봉투로_200을_반환하고_로그인_사용자_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/chat-rooms/{roomId}/read", roomId)
                .with(user(principalOf(me))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<UUID> roomIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> requesterIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(chatRoomService).markAsRead(roomIdCaptor.capture(), requesterIdCaptor.capture());

        assertThat(roomIdCaptor.getValue()).isEqualTo(roomId);
        assertThat(requesterIdCaptor.getValue())
            .as("principal 타입이 틀리면 여기가 null이 된다")
            .isEqualTo(me);
    }

    @Test
    void 비활성_멤버가_읽음_처리를_시도하면_CHAT_ROOM_MEMBERS_001을_반환한다() throws Exception {
        UUID me = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        willThrow(new BusinessException(ErrorCode.CHAT_ROOM_MEMBERS_001, "채팅방의 멤버가 아닙니다."))
            .given(chatRoomService).markAsRead(any(), any());

        mockMvc.perform(post("/api/chat-rooms/{roomId}/read", roomId)
                .with(user(principalOf(me))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CHAT_ROOM_MEMBERS_001"));
    }

    @Test
    void 인증_없이_호출하면_401을_반환한다() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/chat-rooms/{roomId}/read", roomId))
            .andExpect(status().isUnauthorized());
    }
}
