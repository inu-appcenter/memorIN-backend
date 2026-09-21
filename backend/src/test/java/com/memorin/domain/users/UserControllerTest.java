package com.memorin.domain.users;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.users.controller.UserController;
import com.memorin.domain.users.dto.MyPageResponseDto;
import com.memorin.domain.users.dto.UpdateMyProfileRequest;
import com.memorin.domain.users.service.UserService;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.exception.UserDetailsImpl;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void currentUserCanPartiallyUpdateOwnProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        MyPageResponseDto response = new MyPageResponseDto(
            userId, "me@memorin.test", "me", "New name", "Existing bio", null, LocalDateTime.now());
        given(userService.updateMyProfile(org.mockito.ArgumentMatchers.eq(userId), any())).willReturn(response);

        mockMvc.perform(patch("/api/users/me")
                .contentType("application/json")
                .content("{\"displayName\":\"New name\"}")
                .with(user(principalOf(userId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.displayName").value("New name"));

        org.mockito.ArgumentCaptor<UpdateMyProfileRequest> request =
            org.mockito.ArgumentCaptor.forClass(UpdateMyProfileRequest.class);
        verify(userService).updateMyProfile(org.mockito.ArgumentMatchers.eq(userId), request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().hasDisplayName()).isTrue();
        org.assertj.core.api.Assertions.assertThat(request.getValue().hasBio()).isFalse();
        org.assertj.core.api.Assertions.assertThat(request.getValue().hasProfileImageKey()).isFalse();
    }

    @Test
    void invalidDisplayNameIsReportedAsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                .contentType("application/json")
                .content("{\"displayName\":\"\"}")
                .with(user(principalOf(UUID.randomUUID()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_002"));
    }

    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl userDetails = org.mockito.Mockito.mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        given(userDetails.getAuthorities()).willReturn(null);
        return userDetails;
    }
}
