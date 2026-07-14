package com.memorin.global.media.controller;

import com.memorin.global.media.MediaStorageException;
import com.memorin.global.media.StorageQuotaExceededException;
import com.memorin.global.media.dto.request.PresignedUploadRequest;
import com.memorin.global.media.dto.response.PresignedUploadResponse;
import com.memorin.global.media.service.PresignedUploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final PresignedUploadService presignedUploadService;

    public MediaController(PresignedUploadService presignedUploadService) {
        this.presignedUploadService = presignedUploadService;
    }

    @PostMapping("/presigned-upload-url")
    public ResponseEntity<PresignedUploadResponse> createPresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request
    ) {
        return ResponseEntity.ok(presignedUploadService.createUploadUrl(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid presigned upload request."));
    }

    @ExceptionHandler(MediaStorageException.class)
    public ResponseEntity<Map<String, String>> handleStorageException(MediaStorageException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Failed to create presigned upload URL."));
    }

    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<Map<String, String>> handleQuotaExceeded(StorageQuotaExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", e.getMessage()));
    }
}
