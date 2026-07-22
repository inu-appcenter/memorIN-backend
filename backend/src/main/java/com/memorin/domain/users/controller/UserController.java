package com.memorin.domain.users.controller;

import com.memorin.domain.users.service.UserService;
import com.memorin.domain.users.dto.MyPageResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/users")
public class UserController {

    private UserService userService;

    @GetMapping("/me") // 마이 페이지
    public ResponseEntity<MyPageResponseDto> get_my_page(@AuthenticationPrincipal UserDetails userDetails) {
        return null;
    }


}
