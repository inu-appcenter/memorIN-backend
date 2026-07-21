package com.memorin.global.media.controller;

import com.memorin.global.media.dto.request.PresignedUploadRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final PresignedUploadService presignedUploadService;
    private final PresignedDownloadService presignedDownloadService;
    private final StorageQuotaService storageQuotaService;

    public MediaController(
            PresignedUploadService presignedUploadService,
            PresignedDownloadService presignedDownloadService,
            StorageQuotaService storageQuotaService
    ) {
        this.presignedUploadService = presignedUploadService;
        this.presignedDownloadService = presignedDownloadService;
        this.storageQuotaService = storageQuotaService;
    }

    @PostMapping("/presigned-upload-url")
    public ResponseEntity<PresignedUploadResponse> createPresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok(
                presignedUploadService.createUploadUrl(userDetails.getUserId(), request)
        );
    }

    @GetMapping("/quota")
    public ResponseEntity<QuotaResponse> getQuota(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ResponseEntity.ok(storageQuotaService.getQuotaStatus(userDetails.getUserId()));
    }

    @GetMapping("/{postMediaId}/presigned-download-url")
    public ResponseEntity<PresignedDownloadResponse> createPresignedDownloadUrl(
            @PathVariable UUID postMediaId
    ) {
        return ResponseEntity.ok(presignedDownloadService.createDownloadUrl(postMediaId));
    }

    // 이 컨트롤러가 던지는 모든 예외(BusinessException 서브클래스, @Valid 검증 실패 등)는
    // GlobalExceptionHandler가 공통 ApiResponse 포맷으로 처리한다. 로컬 핸들러를 두지 않는다.
}
