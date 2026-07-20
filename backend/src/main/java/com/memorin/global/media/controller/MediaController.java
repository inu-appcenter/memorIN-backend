package com.memorin.global.media.controller;

import com.memorin.global.media.dto.request.PresignedUploadRequest;
import com.memorin.global.media.dto.response.PresignedDownloadResponse;
import com.memorin.global.media.dto.response.PresignedUploadResponse;
import com.memorin.global.media.service.PresignedDownloadService;
import com.memorin.global.media.service.PresignedUploadService;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final PresignedUploadService presignedUploadService;
    private final PresignedDownloadService presignedDownloadService;

    public MediaController(
            PresignedUploadService presignedUploadService,
            PresignedDownloadService presignedDownloadService
    ) {
        this.presignedUploadService = presignedUploadService;
        this.presignedDownloadService = presignedDownloadService;
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

    @GetMapping("/{postMediaId}/presigned-download-url")
    public ResponseEntity<PresignedDownloadResponse> createPresignedDownloadUrl(
            @PathVariable UUID postMediaId
    ) {
        return ResponseEntity.ok(presignedDownloadService.createDownloadUrl(postMediaId));
    }

    // 요청 파라미터 값 자체가 잘못된 경우(예: 잘못된 파일 타입 등) -> 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    // @Valid 바인딩된 요청 DTO가 검증 애노테이션(@NotNull 등)을 통과하지 못한 경우 -> 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid presigned upload request."));
    }

    // MediaStorageException(MEDIA_003), StorageQuotaExceededException(MEDIA_002),
    // PostMediaNotFoundException(MEDIA_004)은 모두 BusinessException이므로
    // GlobalExceptionHandler가 공통 ApiResponse 포맷으로 처리한다.
}
