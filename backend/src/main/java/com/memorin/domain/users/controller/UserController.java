package com.memorin.domain.users.controller;

import com.memorin.domain.users.dto.UserFollowPageResponse;
import com.memorin.domain.users.dto.UserSearchResponse;
import com.memorin.domain.users.service.UserService;
import com.memorin.domain.users.dto.MyPageResponseDto;
import com.memorin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me") // 마이 페이지
    public ResponseEntity<MyPageResponseDto> get_my_page(@AuthenticationPrincipal UserDetails userDetails) {
        return null;
    }

    @GetMapping("/search")
    public ApiResponse<List<UserSearchResponse>> search(@RequestParam String keyword) {
        List<UserSearchResponse> users = userService.searchUsers(keyword);
        return ApiResponse.ok(users);
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<UserFollowPageResponse> followers(
        @PathVariable UUID userId,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(userService.getFollowers(userId, cursor, size));
    }

    @GetMapping("/{userId}/followings")
    public ApiResponse<UserFollowPageResponse> followings(
        @PathVariable UUID userId,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(userService.getFollowings(userId, cursor, size));
    }
}
