package com.example.demo;

import jakarta.persistence.*;

@Entity
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;   // 나중에 JWT 연동 시 실제 유저로 교체

    @Column(unique = true)
    private String token;

    private String deviceType;  // "android", "ios", "web"

    protected FcmToken() {}

    public FcmToken(String userId, String token, String deviceType) {
        this.userId = userId;
        this.token = token;
        this.deviceType = deviceType;
    }

    public String getUserId() { return userId; }
    public String getToken() { return token; }
    public String getDeviceType() { return deviceType; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
}
