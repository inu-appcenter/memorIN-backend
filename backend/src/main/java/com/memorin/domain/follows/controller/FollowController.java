package com.memorin.domain.follows.controller;

import com.memorin.domain.follows.dto.FollowRequest;
import com.memorin.domain.follows.service.FollowService;
import com.memorin.domain.users.dto.UserFollowRequestResponse;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Tag(name = "팔로우", description = "팔로우 요청 · 수락 · 거절/취소")
@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // 팔로우 요청
    @Operation(summary = "팔로우 요청", description = "followingId 사용자에게 팔로우를 요청한다(PENDING 상태 생성).")
    @PostMapping
    public ApiResponse<Void> request(@RequestBody @Valid FollowRequest request, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        followService.request(userDetails.getUserId(), request.followingId());
        return ApiResponse.ok();
    }

    // 팔로우 수락
    @Operation(summary = "팔로우 수락", description = "받은 팔로우 요청을 수락한다(ACCEPTED). 요청 대상 본인만 가능.")
    @PatchMapping("/{followId}/accept")
    public ApiResponse<Void> accept(@PathVariable UUID followId, @AuthenticationPrincipal UserDetailsImpl userDetails){
        followService.accept(followId, userDetails.getUserId());
        return ApiResponse.ok();
    }

    // 팔로우 거절
    @Operation(summary = "팔로우 거절/취소", description = "팔로우 요청을 거절하거나 기존 팔로우 관계를 해제한다.")
    @DeleteMapping("/{followingId}")
    public ApiResponse<Void> reject(@PathVariable UUID followingId, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        followService.reject(userDetails.getUserId(), followingId);
        return ApiResponse.ok();
    }

    @GetMapping("/requests")
    public ApiResponse<List<UserFollowRequestResponse>> getReceivedRequests(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.ok(followService.getFollowRequests(userDetails.getUserId()));
    }
}
