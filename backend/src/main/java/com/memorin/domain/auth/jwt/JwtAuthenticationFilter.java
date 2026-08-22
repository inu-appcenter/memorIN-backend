package com.memorin.domain.auth.jwt;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.config.RestAuthenticationEntryPoint;
import com.memorin.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = jwtTokenProvider.resolveToken(request);

        if (token != null) {
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (BusinessException e) {
                ErrorCode errorCode = e.getErrorCode();

                if (errorCode == ErrorCode.AUTH_003 || errorCode == ErrorCode.AUTH_004) {
                    SecurityContextHolder.clearContext();
                    restAuthenticationEntryPoint.writeError(response, errorCode);
                    return;
                }

                throw e;
            }
        }

        filterChain.doFilter(request, response);
    }
}
