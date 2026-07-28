package com.memorin.domain.fcm_token.dto;

import com.memorin.domain.fcm_token.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FcmTokenRequest(

    @NotBlank
    String token,

    @NotNull
    DeviceType deviceType
){
}
