package com.memorin.domain.notifications.controller;

import com.memorin.domain.notifications.dto.NotificationPageResponse;
import com.memorin.domain.notifications.service.NotificationService;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "알림 API")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "알림 히스토리 조회")
    public ApiResponse<NotificationPageResponse> getNotifications(
        @AuthenticationPrincipal UserDetailsImpl userDetails,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(required = false) Integer size
    ) {

        NotificationPageResponse response = notificationService.getNotifications(userDetails.getUserId(), cursor, size);

        return ApiResponse.ok(response);
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    public ApiResponse<Void> read(
        @AuthenticationPrincipal UserDetailsImpl userDetails,
        @PathVariable UUID notificationId
    ) {
        notificationService.read(userDetails.getUserId(), notificationId);

        return ApiResponse.ok();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "알림 전체 읽음 처리")
    public ApiResponse<Void> readAll(
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        notificationService.readAll(userDetails.getUserId());

        return ApiResponse.ok();
    }
}
