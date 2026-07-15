package com.memorin.global.media.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.global.config.SecurityConfig;
import com.memorin.global.media.MediaStorageException;
import com.memorin.global.media.PostMediaNotFoundException;
import com.memorin.global.media.StorageQuotaExceededException;
import com.memorin.global.media.dto.request.PresignedUploadRequest;
import com.memorin.global.media.service.PresignedDownloadService;
import com.memorin.global.media.service.PresignedUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MediaController의 웹 계층(요청 매핑, 예외 -> 응답 변환)만 검증한다.
// 실제 MinIO/DB 연동은 서비스 빈을 목으로 대체해 제외한다.
// SecurityConfig를 import해 /api/media/** permitAll 규칙을 실제와 동일하게 적용한다.
@WebMvcTest(MediaController.class)
@Import(SecurityConfig.class)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PresignedUploadService presignedUploadService;

    @MockitoBean
    private PresignedDownloadService presignedDownloadService;

    @Test
    void createPresignedUploadUrl_quota를_초과하면_403과_MEDIA_002_에러코드를_반환한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        PresignedUploadRequest request = new PresignedUploadRequest(
                "photo.png", "image/png", 10_000_000L, userId
        );
        given(presignedUploadService.createUploadUrl(any(PresignedUploadRequest.class)))
                .willThrow(new StorageQuotaExceededException(userId, 9_000_000_000L, 10_000_000L, 9_000_000_000L));

        // when
        // then
        mockMvc.perform(post("/api/media/presigned-upload-url")
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
        PresignedUploadRequest request = new PresignedUploadRequest(
                "photo.png", "image/png", 10_000_000L, userId
        );
        given(presignedUploadService.createUploadUrl(any(PresignedUploadRequest.class)))
                .willThrow(new MediaStorageException("Presigned upload URL creation failed.", new RuntimeException("minio down")));

        // when
        // then
        mockMvc.perform(post("/api/media/presigned-upload-url")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_003"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void createPresignedDownloadUrl_미디어를_찾지_못하면_404와_MEDIA_004_에러코드를_반환한다() throws Exception {
        // given
        UUID postMediaId = UUID.randomUUID();
        given(presignedDownloadService.createDownloadUrl(postMediaId))
                .willThrow(new PostMediaNotFoundException(postMediaId));

        // when
        // then
        mockMvc.perform(get("/api/media/{postMediaId}/presigned-download-url", postMediaId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEDIA_004"))
                .andExpect(jsonPath("$.error.message").exists());
    }
}
