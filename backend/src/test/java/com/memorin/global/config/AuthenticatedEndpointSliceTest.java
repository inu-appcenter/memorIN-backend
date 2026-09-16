package com.memorin.global.config;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.follows.controller.FollowController;
import com.memorin.domain.follows.service.FollowService;
import com.memorin.domain.messages.controller.MessageController;
import com.memorin.domain.messages.dto.response.MessagePageResponse;
import com.memorin.domain.messages.service.MessageService;
import com.memorin.domain.notifications.controller.NotificationController;
import com.memorin.domain.notifications.dto.NotificationPageResponse;
import com.memorin.domain.notifications.service.NotificationService;
import com.memorin.global.exception.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "로그인한 사람이 누구인지"가 서비스까지 제대로 전달되는지 컨트롤러를 지나가며 검증한다.
 *
 * <p>{@code ChatRoomSearchAuthTest}가 만든 패턴을 커버리지가 0이던 도메인으로 넓힌 것이다.
 * 여기 고른 셋은 <b>principal이 틀렸을 때 에러가 아니라 "남의 데이터"가 나오는</b> 경로다.
 *
 * <ul>
 *   <li>알림 — {@code userId}가 틀리면 남의 알림을 보거나 읽음 처리한다</li>
 *   <li>팔로우 요청 목록 — 내가 받은 요청이 아니라 남이 받은 요청이 나온다</li>
 *   <li>메시지 히스토리 — 방 멤버 검사의 기준이 되는 사용자가 틀어진다</li>
 * </ul>
 *
 * <p>500이 나는 쪽은 운영에서 금방 드러나지만 이쪽은 드러나지 않는다. 그래서 상태코드가 아니라
 * <b>서비스에 도달한 UUID를 직접 붙잡아</b> 확인한다 — 200이 떨어지는 것만 봐서는 잡을 수 없다.
 *
 * <p>타입 자체가 틀린 경우는 {@code AuthenticationPrincipalContractTest}가 전체 엔드포인트에 대해
 * 한 번에 막는다. 이 파일은 그 위에서 "올바른 값이 올바른 자리로 가는가"를 본다.
 * 남은 공백 목록은 {@code docs/controller-test-coverage.md}에 있다.
 */
@WebMvcTest({NotificationController.class, FollowController.class, MessageController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
    RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthenticatedEndpointSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private FollowService followService;

    @MockitoBean
    private MessageService messageService;

    // MessageController가 주입받는 협력자라 슬라이스 구성에 필요하다. 여기서 호출하지는 않는다.
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        given(userDetails.getAuthorities()).willReturn(null);
        return userDetails;
    }

    @Test
    void 알림_목록은_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        given(notificationService.getNotifications(any(), any(), any()))
            .willReturn(new NotificationPageResponse(List.of(), null, false));

        mockMvc.perform(get("/api/notifications").with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> userId = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService).getNotifications(userId.capture(), any(), any());

        assertThat(userId.getValue())
            .as("이 값이 틀리면 남의 알림이 내려간다 — 에러는 나지 않는다")
            .isEqualTo(me);
    }

    @Test
    void 알림_읽음_처리는_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                .with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> userId = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService).read(userId.capture(), any());

        assertThat(userId.getValue())
            .as("소유자 검사의 기준이 되는 값이다. 틀리면 남의 알림을 읽음 처리한다")
            .isEqualTo(me);
    }

    @Test
    void 알림_전체_읽음은_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();

        mockMvc.perform(patch("/api/notifications/read-all").with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> userId = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService).readAll(userId.capture());

        assertThat(userId.getValue()).isEqualTo(me);
    }

    @Test
    void 받은_팔로우_요청_목록은_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        given(followService.getFollowRequests(any())).willReturn(List.of());

        mockMvc.perform(get("/api/follows/requests").with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> userId = ArgumentCaptor.forClass(UUID.class);
        verify(followService).getFollowRequests(userId.capture());

        assertThat(userId.getValue())
            .as("내가 받은 요청이어야 한다. 틀리면 남이 받은 요청 목록이 나온다")
            .isEqualTo(me);
    }

    @Test
    void 팔로우_요청_수락은_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        mockMvc.perform(patch("/api/follows/{followId}/accept", followId)
                .with(user(principalOf(me))))
            .andExpect(status().isOk());

        // accept(followId, userId) — 인자 순서가 뒤집혀도 컴파일은 된다. 둘 다 UUID다.
        ArgumentCaptor<UUID> first = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> second = ArgumentCaptor.forClass(UUID.class);
        verify(followService).accept(first.capture(), second.capture());

        assertThat(first.getValue()).as("첫 번째 인자는 팔로우 행 id").isEqualTo(followId);
        assertThat(second.getValue()).as("두 번째 인자는 수신자 본인 검사의 기준").isEqualTo(me);
    }

    @Test
    void 메시지_히스토리는_로그인_사용자의_id를_서비스로_넘긴다() throws Exception {
        UUID me = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(messageService.getMessages(any(), any(), any(), any()))
            .willReturn(new MessagePageResponse(List.of(), null, false));

        mockMvc.perform(get("/api/chat-rooms/{roomId}/messages", roomId)
                .with(user(principalOf(me))))
            .andExpect(status().isOk());

        ArgumentCaptor<UUID> userId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> room = ArgumentCaptor.forClass(UUID.class);
        verify(messageService).getMessages(userId.capture(), room.capture(), any(), any());

        assertThat(userId.getValue())
            .as("방 활성 멤버 검사의 기준이다. 틀리면 인가 판정 자체가 엉뚱한 사람으로 돈다")
            .isEqualTo(me);
        assertThat(room.getValue()).isEqualTo(roomId);
    }
}
