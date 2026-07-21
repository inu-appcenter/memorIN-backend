package com.memorin.domain.users.dto;

import jakarta.validation.constraints.NotBlank;

// 마이 페이지 조회 응답 dto
public record MyPageResponseDto(

        @NotBlank
        String username,

        @NotBlank
        String displayName,

        @NotBlank
        String bio

) {
}
