package com.memorin.domain.auth.controller;

import com.memorin.domain.auth.dto.LoginRequest;
import com.memorin.domain.auth.dto.LoginResponse;
import com.memorin.domain.auth.dto.RefreshTokenRequest;
import com.memorin.domain.auth.dto.SignupRequest;
import com.memorin.domain.auth.service.AuthService;
import com.memorin.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="인증", description="회원가입 · 로그인 · 토큰 재발급")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일·비밀번호로 사용자를 생성한다. 비밀번호는 BCrypt로 해시 저장.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 유저/username")
    
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> signup(@RequestBody @Valid SignupRequest request) {
        authService.signup(request);
        return ApiResponse.ok();
    }

    @Operation(summary = "로그인", description = "이메일·비밀번호를 검증하고 Access/Refresh 토큰을 발급한다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.ok(response);
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 새 Access Token을 재발급한다.")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.reissue(request.refreshToken()));
    }
}
