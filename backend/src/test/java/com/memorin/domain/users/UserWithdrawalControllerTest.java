package com.memorin.domain.users;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.users.controller.UserController;
import com.memorin.domain.users.service.UserService;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.exception.UserDetailsImpl;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserWithdrawalControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void authenticatedUserCanWithdrawOnlyOwnAccount() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/users/me").with(user(principalOf(userId))))
            .andExpect(status().isNoContent());

        verify(userService).withdraw(userId);
    }

    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl principal = org.mockito.Mockito.mock(UserDetailsImpl.class);
        given(principal.getUserId()).willReturn(userId);
        given(principal.getAuthorities()).willReturn(null);
        return principal;
    }
}
