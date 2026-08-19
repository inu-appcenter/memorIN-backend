package com.memorin.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 인증되지 않은 요청에 대한 응답을 401 + 전역 ApiResponse 포맷으로 통일한다.
// 이 빈이 없으면 Spring Security 기본값(Http403ForbiddenEntryPoint)이 403을 내려,
// 클라이언트가 "인증 필요(401)"와 "Quota 초과(MEDIA_002, 403)"를 구분할 수 없다.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        writeError(response, ErrorCode.AUTH_001);
    }

    public void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
