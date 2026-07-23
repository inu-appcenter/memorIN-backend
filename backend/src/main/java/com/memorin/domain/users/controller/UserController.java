package com.memorin.domain.users.controller;

import com.memorin.domain.users.dto.UserSearchResponse;
import com.memorin.domain.users.service.UserService;
import com.memorin.domain.users.dto.MyPageResponseDto;
import com.memorin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
