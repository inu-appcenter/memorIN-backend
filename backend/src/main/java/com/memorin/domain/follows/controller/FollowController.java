package com.memorin.domain.follows.controller;

import com.memorin.domain.follows.dto.FollowRequest;
import com.memorin.domain.follows.service.FollowService;
import com.memorin.domain.users.entity.User;
import com.memorin.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // 팔로우 요청
    @PostMapping
    public ApiResponse<Void> request(@RequestBody @Valid FollowRequest request, @AuthenticationPrincipal User user) {
        followService.request(user.getId(), request.followingId());
        return ApiResponse.ok();
    }

    // 팔로우 수락
    @PatchMapping("/{followId}/accept")
    public ApiResponse<Void> accept(@PathVariable UUID followId, @AuthenticationPrincipal User user){
        followService.accept(followId, user.getId());
        return ApiResponse.ok();
    }

    // 팔로우 거절
    @DeleteMapping("/{followingId}")
    public ApiResponse<Void> reject(@PathVariable UUID followingId, @AuthenticationPrincipal User user) {
        followService.reject(user.getId(), followingId);
        return ApiResponse.ok();
    }
}
