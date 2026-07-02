package com.memorin.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {

    @NotBlank //빈 문자열, 공백만 있는 값 방지
    @Email //이메일 형식과 일치하는지 검사
    private String email;

    @NotBlank
    //@Size(min = , max = )
    private String password;

    @NotBlank
    //@Size(min = , max = )
    private String username;

    @NotBlank
    //@Size(min = , max = )
    private String displayName;
}
