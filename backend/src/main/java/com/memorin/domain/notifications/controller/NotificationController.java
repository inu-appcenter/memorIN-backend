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
@Tag(name = "알림", description = "알림 히스토리 조회 · 읽음 처리")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "알림 히스토리 조회",
        description = "내 알림을 최신순 커서 페이지네이션으로 조회한다. cursor는 직전 페이지의 nextCursor를 그대로 넣는다.")
    public ApiResponse<NotificationPageResponse> getNotifications(
        @AuthenticationPrincipal UserDetailsImpl userDetails,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(required = false) Integer size
    ) {

        NotificationPageResponse response = notificationService.getNotifications(userDetails.getUserId(), cursor, size);

        return ApiResponse.ok(response);
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "알림 하나를 읽음 상태로 바꾼다. 내 알림이 아니면 실패한다.")
    public ApiResponse<Void> read(
        @AuthenticationPrincipal UserDetailsImpl userDetails,
        @PathVariable UUID notificationId
    ) {
        notificationService.read(userDetails.getUserId(), notificationId);

        return ApiResponse.ok();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "알림 전체 읽음 처리", description = "읽지 않은 내 알림을 모두 읽음 처리한다.")
    public ApiResponse<Void> readAll(
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        notificationService.readAll(userDetails.getUserId());

        return ApiResponse.ok();
    }
}
