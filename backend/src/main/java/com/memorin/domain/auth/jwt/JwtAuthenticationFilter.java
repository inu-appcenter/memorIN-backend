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
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    /**
     * permitAll 경로 중 토큰 검증을 아예 건너뛰는 곳(#309).
     *
     * <p>여기서 검증하면 만료되거나 잘못된 Bearer가 붙은 채로 들어온 요청이 401로 끝나서,
     * 만료된 access 토큰을 붙인 채 refresh하는 클라이언트가 재발급을 받지 못한다.
     * {@code /auth/logout}은 인증이 필요한 경로라 넣지 않는다 — 건너뛰면 정상 토큰으로도
     * 인증이 안 돼 항상 401이 되고, 만료 토큰일 때 클라이언트가 보던 AUTH_003도 사라진다.
     * SecurityConfig의 permitAll 목록과 함께 고친다.
     */
    static final Set<String> SKIP_PATHS = Set.of(
            "/auth/signup",
            "/auth/login",
            "/auth/refresh",
            "/api/health"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return SKIP_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = jwtTokenProvider.resolveToken(request);

        if (token != null) {
            try {
                if (jwtTokenProvider.validateAccessToken(token)) {
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
