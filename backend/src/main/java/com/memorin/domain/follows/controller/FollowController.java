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

    // 팔로우 취소/언팔로우
    @Operation(summary = "팔로우 취소/언팔로우",
        description = """
            path의 followingId는 **내가 팔로우한(또는 요청한) 상대**다. (follower=나, following=path) 관계를 삭제한다.
            받은 요청의 '거절'은 (follower=상대, following=나) 방향이라 이 API로는 처리되지 않는다 — 항상 FOLLOW_001이다(#163).""")
    @DeleteMapping("/{followingId}")
    public ApiResponse<Void> reject(@PathVariable UUID followingId, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        followService.reject(userDetails.getUserId(), followingId);
        return ApiResponse.ok();
    }

    // 받은 요청 목록
    @Operation(summary = "받은 팔로우 요청 목록",
        description = """
            내게 온 PENDING 상태의 팔로우 요청을 최신순으로 조회한다.
            응답의 followId를 수락(PATCH /api/follows/{followId}/accept)에 그대로 쓴다.
            페이지네이션이 없어 요청이 많으면 전부 내려간다 — 커서 페이징 전환은 별도 이슈.""")
    @GetMapping("/requests")
    public ApiResponse<List<UserFollowRequestResponse>> getReceivedRequests(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.ok(followService.getFollowRequests(userDetails.getUserId()));
    }
}
