package com.example.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fcm")
public class FcmTokenController {

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmService fcmService;

    public FcmTokenController(FcmTokenRepository fcmTokenRepository, FcmService fcmService) {
        this.fcmTokenRepository = fcmTokenRepository;
        this.fcmService = fcmService;
    }

    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(@RequestBody FcmTokenRequest request) {
        // 같은 토큰이 이미 있으면 소유자/기기정보만 갱신 (중복 row 방지)
        FcmToken token = fcmTokenRepository.findByToken(request.token())
                .orElseGet(() -> new FcmToken(request.userId(), request.token(), request.deviceType()));
        token.setUserId(request.userId());
        token.setDeviceType(request.deviceType());
        fcmTokenRepository.save(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Void> testFcm(@RequestParam String userId) {
        fcmService.sendMessage(userId, "테스트 알림", "FCM 연동 성공!");
        return ResponseEntity.ok().build();
    }
}