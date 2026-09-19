package com.memorin.domain.fcm_token;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.fcm_token.controller.FcmTokenController;
import com.memorin.domain.fcm_token.service.FcmTokenService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FcmTokenController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class FcmTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FcmTokenService fcmTokenService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void currentUserCanDeleteOnlyTheTokenIncludedInTheRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "current-device-token";

        mockMvc.perform(delete("/api/fcm/token")
                .contentType("application/json")
                .content("{\"token\":\"" + token + "\"}")
                .with(user(principalOf(userId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(fcmTokenService).delete(userId, token);
    }

    @Test
    void missingTokenIsRejectedBeforeDeletion() throws Exception {
        mockMvc.perform(delete("/api/fcm/token")
                .contentType("application/json")
                .content("{\"token\":\"\"}")
                .with(user(principalOf(UUID.randomUUID()))))
            .andExpect(status().isBadRequest());
    }

    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl userDetails = org.mockito.Mockito.mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        given(userDetails.getAuthorities()).willReturn(null);
        return userDetails;
    }
}
