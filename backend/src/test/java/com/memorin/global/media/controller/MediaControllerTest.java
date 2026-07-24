package com.memorin.global.media.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.global.config.RestAccessDeniedHandler;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.exception.UserDetailsImpl;
import com.memorin.global.media.dto.request.PresignedUploadRequest;
import com.memorin.global.media.dto.response.QuotaResponse;
import com.memorin.global.media.exception.MediaStorageException;
import com.memorin.global.media.exception.PostMediaNotFoundException;
import com.memorin.global.media.exception.StorageQuotaExceededException;
import com.memorin.global.media.exception.UnsupportedContentTypeException;
import com.memorin.global.media.service.PresignedDownloadService;
import com.memorin.global.media.service.PresignedUploadService;
import com.memorin.global.media.service.StorageQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MediaController의 웹 계층(요청 매핑, 인증, 예외 -> 응답 변환)만 검증한다.
// 실제 MinIO/DB 연동은 서비스 빈을 목으로 대체해 제외한다.
// SecurityConfig를 import해 실제 인가 규칙을 그대로 적용한다.
// JwtAuthenticationFilter는 실물을 넣고 의존성인 JwtTokenProvider만 목으로 대체한다.
// 필터 자체를 목으로 만들면 doFilter가 아무것도 하지 않아 요청이 컨트롤러까지 도달하지 못한다.
@WebMvcTest(MediaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PresignedUploadService presignedUploadService;

    @MockitoBean
    private PresignedDownloadService presignedDownloadService;

    @MockitoBean
    private StorageQuotaService storageQuotaService;

    // resolveToken이 null을 반환하므로 필터는 인증을 건드리지 않고 통과시킨다.
    // 인증 상태는 아래 .with(user(...))로 직접 세팅한다.
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // JWT 필터가 SecurityContext에 넣어주는 principal과 동일한 타입으로 인증을 흉내낸다.
    private UserDetails principalOf(UUID userId) {
        UserDetailsImpl userDetails = org.mockito.Mockito.mock(UserDetailsImpl.class);
        given(userDetails.getUserId()).willReturn(userId);
        given(userDetails.getAuthorities()).willReturn(null);
        return userDetails;
    }

    @Test
    void createPresignedUploadUrl_인증이_없으면_401을_반환한다() throws Exception {
        // given
        PresignedUploadRequest request = new PresignedUploadRequest("photo.png", "image/png", 10_000_000L);

        // when
        // then
        mockMvc.perform(post("/api/media/presigned-upload-url")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Quota 초과(MEDIA_002, 403)와 구분되어야 한다.
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void createPresignedUploadUrl_quota를_초과하면_403과_MEDIA_002_에러코드를_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        PresignedUploadRequest request = new PresignedUploadRequest("photo.png", "image/png", 10_000_000L);
        given(presignedUploadService.createUploadUrl(eq(userId), any(PresignedUploadRequest.class)))
                .willThrow(new StorageQuotaExceededException(userId, 9_000_000_000L, 10_000_000L, 9_000_000_000L));

        // when
        // then
        mockMvc.perform(post("/api/media/presigned-upload-url")
                        .with(user(principalOf(userId)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_002"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void createPresignedUploadUrl_스토리지_연동에_실패하면_500과_MEDIA_003_에러코드를_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        PresignedUploadRequest request = new PresignedUploadRequest("photo.png", "image/png", 10_000_000L);
        given(presignedUploadService.createUploadUrl(eq(userId), any(PresignedUploadRequest.class)))
                .willThrow(new MediaStorageException("Presigned upload URL creation failed.", new RuntimeException("minio down")));

        // when
        // then
        mockMvc.perform(post("/api/media/presigned-upload-url")
                        .with(user(principalOf(userId)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_003"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void createPresignedUploadUrl_허용되지_않는_파일_형식이면_400과_MEDIA_001_에러코드를_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        PresignedUploadRequest request = new PresignedUploadRequest("malware.exe", "application/x-msdownload", 10_000_000L);
        given(presignedUploadService.createUploadUrl(eq(userId), any(PresignedUploadRequest.class)))
                .willThrow(new UnsupportedContentTypeException("application/x-msdownload"));

        // when
        // then
        mockMvc.perform(post("/api/media/presigned-upload-url")
                        .with(user(principalOf(userId)))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_001"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void getQuota_인증이_없으면_401을_반환한다() throws Exception {
        // when
        // then
        mockMvc.perform(get("/api/media/quota"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void getQuota_인증된_사용자의_사용량을_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(storageQuotaService.getQuotaStatus(userId))
                .willReturn(new QuotaResponse(300_000_000L, 1_073_741_824L, 773_741_824L, 27.94));

        // when
        // then
        mockMvc.perform(get("/api/media/quota")
                        .with(user(principalOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedBytes").value(300_000_000L))
                .andExpect(jsonPath("$.limitBytes").value(1_073_741_824L))
                .andExpect(jsonPath("$.remainingBytes").value(773_741_824L))
                .andExpect(jsonPath("$.usagePercentage").value(27.94));
    }

    @Test
    void createPresignedDownloadUrl_미디어를_찾지_못하면_404와_MEDIA_004_에러코드를_반환한다() throws Exception {
        // given
        UUID postMediaId = UUID.randomUUID();
        given(presignedDownloadService.createDownloadUrl(postMediaId))
                .willThrow(new PostMediaNotFoundException(postMediaId));

        // when
        // then
        mockMvc.perform(get("/api/media/{postMediaId}/presigned-download-url", postMediaId)
                        .with(user(principalOf(UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_004"))
                .andExpect(jsonPath("$.error.message").exists());
    }
}
