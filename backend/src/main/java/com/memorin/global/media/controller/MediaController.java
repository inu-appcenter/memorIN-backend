package com.memorin.global.media.controller;

import com.memorin.global.media.MediaCompressionProperties;
import com.memorin.global.media.dto.request.PresignedUploadRequest;
import com.memorin.global.media.dto.response.CompressionPolicyResponse;
import com.memorin.global.media.dto.response.PresignedDownloadResponse;
import com.memorin.global.media.dto.response.PresignedUploadResponse;
import com.memorin.global.media.dto.response.QuotaResponse;
import com.memorin.global.media.service.PresignedDownloadService;
import com.memorin.global.media.service.PresignedUploadService;
import com.memorin.global.media.service.StorageQuotaService;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "미디어", description = "presigned 업로드/다운로드 URL · 저장용량 · 압축 가이드")
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final PresignedUploadService presignedUploadService;
    private final PresignedDownloadService presignedDownloadService;
    private final StorageQuotaService storageQuotaService;
    private final MediaCompressionProperties compressionProperties;

    public MediaController(
            PresignedUploadService presignedUploadService,
            PresignedDownloadService presignedDownloadService,
            StorageQuotaService storageQuotaService,
            MediaCompressionProperties compressionProperties
    ) {
        this.presignedUploadService = presignedUploadService;
        this.presignedDownloadService = presignedDownloadService;
        this.storageQuotaService = storageQuotaService;
        this.compressionProperties = compressionProperties;
    }

    @Operation(summary = "업로드 URL 발급", description = "클라이언트가 MinIO에 직접 PUT 업로드할 presigned URL을 발급한다.")
    @PostMapping("/presigned-upload-url")
    public ResponseEntity<PresignedUploadResponse> createPresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok(
                presignedUploadService.createUploadUrl(userDetails.getUserId(), request)
        );
    }

    // 서버는 업로드 파일 바이트를 직접 만지지 않으므로(presigned PUT), 실제 압축은 클라이언트가 수행한다.
    // 이 값들은 클라이언트가 업로드 전 압축 시 참고할 가이드일 뿐 서버가 강제하지 않는다.
    @Operation(summary = "압축 가이드 조회", description = "클라이언트 업로드 전 이미지 압축 참고값(서버 강제 아님).")
    @GetMapping("/compression-policy")
    public ResponseEntity<CompressionPolicyResponse> getCompressionPolicy() {
        return ResponseEntity.ok(new CompressionPolicyResponse(
                compressionProperties.imageQualityPercent(),
                compressionProperties.imageMaxWidthPx(),
                compressionProperties.imageMaxHeightPx()
        ));
    }

    @Operation(summary = "저장용량 조회", description = "로그인 사용자의 스토리지 사용량/한도를 조회한다.")
    @GetMapping("/quota")
    public ResponseEntity<QuotaResponse> getQuota(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok(storageQuotaService.getQuotaStatus(userDetails.getUserId()));
    }

    @Operation(summary = "다운로드 URL 발급", description = "postMediaId의 미디어를 내려받을 presigned URL을 발급한다.")
    @GetMapping("/{postMediaId}/presigned-download-url")
    public ResponseEntity<PresignedDownloadResponse> createPresignedDownloadUrl(
            @PathVariable UUID postMediaId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok(
                presignedDownloadService.createDownloadUrl(postMediaId, userDetails.getUserId())

        );
    }

    // 이 컨트롤러가 던지는 모든 예외(BusinessException 서브클래스, @Valid 검증 실패 등)는
    // GlobalExceptionHandler가 공통 ApiResponse 포맷으로 처리한다. 로컬 핸들러를 두지 않는다.
}
