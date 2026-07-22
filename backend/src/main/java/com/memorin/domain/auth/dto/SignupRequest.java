package com.memorin.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest (

    @NotBlank //빈 문자열, 공백만 있는 값 방지
    @Email //이메일 형식과 일치하는지 검사
    String email,

    @NotBlank
    @Size(min = 8, max = 64)
    String password,

    @NotBlank
    @Size(max = 50)
    String username,

    @NotBlank
    @Size(max = 50)
    String displayName,

    @Size(max = 200)
    String bio

) {
}
