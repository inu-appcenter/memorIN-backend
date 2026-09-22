package com.memorin.global.instance.controller;

import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstanceInfoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class InstanceInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstanceInfoController instanceInfoController;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private BuildProperties buildProperties;

    @BeforeEach
    void setUp() {
        given(buildProperties.getVersion()).willReturn("test-build-version");
    }

    @Test
    void anonymous_request_receives_only_public_instance_fields() throws Exception {
        mockMvc.perform(get("/api/instance/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("memorin"))
            .andExpect(jsonPath("$.description").value("A memorIN instance"))
            .andExpect(jsonPath("$.public").value(true))
            .andExpect(jsonPath("$.signupEnabled").value(true))
            .andExpect(jsonPath("$.version").value("test-build-version"))
            .andExpect(jsonPath("$.userCount").doesNotExist());
    }

    @Test
    void private_instance_is_not_discoverable() throws Exception {
        ReflectionTestUtils.setField(instanceInfoController, "instancePublic", false);

        mockMvc.perform(get("/api/instance/info"))
            .andExpect(status().isNotFound());
    }
}
