package com.memorin.domain.fcm_token.controller;

import com.memorin.domain.fcm_token.dto.FcmTokenRequest;
import com.memorin.domain.fcm_token.service.FcmTokenService;
import com.memorin.domain.users.entity.User;
import com.memorin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fcm")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @PostMapping("/token")
    public ApiResponse<Void> save(@AuthenticationPrincipal User user, @RequestBody FcmTokenRequest request) {
        fcmTokenService.save(user, request);
        return ApiResponse.ok();
    }
}
