package com.memorin.auth.dto;

import lombok.Getter;

@Getter
public class LoginResponse {

    private final String accessToken;

    public LoginResponse(String accessToken) {
        this.accessToken = accessToken;
    }
}
