package com.memorin.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 인증은 됐지만 권한이 없는 요청에 대한 응답을 403 + 전역 ApiResponse 포맷으로 통일한다.
// 이 빈이 없으면 Spring Security 기본값이 빈 본문 또는 서블릿 컨테이너의 HTML 오류 페이지를 내려,
// 403만 공통 응답 봉투 밖으로 새어 나간다.
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(ErrorCode.COMMON_003.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(ErrorCode.COMMON_003));
    }
}
