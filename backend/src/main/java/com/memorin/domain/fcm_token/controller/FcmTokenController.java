package com.memorin.domain.fcm_token.controller;

import com.memorin.domain.fcm_token.dto.FcmTokenRequest;
import com.memorin.domain.fcm_token.service.FcmTokenService;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "FCM 토큰", description = "푸시 알림용 FCM 디바이스 토큰 등록")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fcm")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @Operation(summary = "FCM 토큰 등록", description = "로그인 사용자의 FCM 디바이스 토큰을 저장/갱신한다.")
    @PostMapping("/token")
    public ApiResponse<Void> save(@AuthenticationPrincipal UserDetailsImpl userDetails, @RequestBody FcmTokenRequest request) {
        fcmTokenService.save(userDetails.getUserId(), request);
        return ApiResponse.ok();
    }
}
