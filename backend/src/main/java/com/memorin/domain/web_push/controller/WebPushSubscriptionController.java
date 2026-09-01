package com.memorin.domain.web_push.controller;

import com.memorin.domain.web_push.dto.WebPushSubscriptionDeleteRequest;
import com.memorin.domain.web_push.dto.WebPushSubscriptionRequest;
import com.memorin.domain.web_push.service.WebPushSubscriptionService;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Web Push", description = "브라우저 푸시 구독 등록 및 해제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/web-push/subscriptions")
public class WebPushSubscriptionController {

    private final WebPushSubscriptionService subscriptionService;

    @Operation(summary = "Web Push 구독 등록 또는 갱신")
    @PostMapping
    public ApiResponse<Void> save(
        @AuthenticationPrincipal UserDetailsImpl userDetails,
        @Valid @RequestBody WebPushSubscriptionRequest request
    ) {
        subscriptionService.save(userDetails.getUserId(), request);
        return ApiResponse.ok();
    }

    @Operation(summary = "Web Push 구독 해제")
    @DeleteMapping
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal UserDetailsImpl userDetails,
        @Valid @RequestBody WebPushSubscriptionDeleteRequest request
    ) {
        subscriptionService.delete(userDetails.getUserId(), request.endpoint());
        return ApiResponse.ok();
    }
}
