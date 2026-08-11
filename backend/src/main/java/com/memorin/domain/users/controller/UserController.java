package com.memorin.domain.users.controller;

import com.memorin.domain.users.dto.UserFollowPageResponse;
import com.memorin.domain.users.dto.UserProfileResponse;
import com.memorin.domain.users.dto.UserSearchPageResponse;
import com.memorin.domain.users.service.UserService;
import com.memorin.domain.users.dto.MyPageResponseDto;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "사용자", description = "내 정보 · 사용자 검색 · 팔로워/팔로잉 목록")
@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 마이페이지 정보를 조회한다.")
    @GetMapping("/me") // 마이 페이지
    public ResponseEntity<ApiResponse<MyPageResponseDto>> getMyPage(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        MyPageResponseDto response = userService.getMyPage(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "사용자 검색", description = "keyword로 사용자를 검색한다. cursor·size 커서 페이지네이션.")
    @GetMapping("/search")
    public ApiResponse<UserSearchPageResponse> search(
        @RequestParam String keyword,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(required = false) Integer size
    ) {
        UserSearchPageResponse response = userService.searchUsers(keyword, cursor, size);
        return ApiResponse.ok(response);
    }

    @Operation(summary = "팔로워 목록", description = "해당 사용자를 팔로우하는 사람 목록(ACCEPTED).")
    @GetMapping("/{userId}/followers")
    public ApiResponse<UserFollowPageResponse> followers(
        @PathVariable UUID userId,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(userService.getFollowers(userId, cursor, size));
    }

    @Operation(summary = "팔로잉 목록", description = "해당 사용자가 팔로우하는 사람 목록(ACCEPTED).")
    @GetMapping("/{userId}/followings")
    public ApiResponse<UserFollowPageResponse> followings(
        @PathVariable UUID userId,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(userService.getFollowings(userId, cursor, size));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 공개 프로필 조회")
    public ResponseEntity<UserProfileResponse> getPublicProfile(
        @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(userService.getPublicProfile(userId));
    }
}
