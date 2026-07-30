package com.memorin.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.memorin.domain.auth.jwt.JwtAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults()) // CorsConfig의 CorsConfigurationSource 빈을 자동으로 사용
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) //세션을 만들지 않음(JWT)
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                // 인증 실패는 401(AUTH_001)로 내린다. 기본값을 두면 403이라 Quota 초과와 구분되지 않는다.
                // 권한 부족은 403(COMMON_003)으로 내린다. 핸들러를 지정하지 않으면 이 응답만
                // ApiResponse 봉투 밖(빈 본문/HTML 오류 페이지)으로 나간다.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/signup", "/auth/login", "/auth/refresh").permitAll()
                        // /api/media/**는 JWT 필터 도입에 맞춰 permitAll에서 제외했다.
                        // Quota 검증 대상 userId를 토큰에서 받으므로 인증 없이 열어두면 남의 quota로 업로드가 가능해진다.
                        .requestMatchers("/ws/**", "/*.html", "/error").permitAll()
                        // API 문서. 운영 배포 시 노출 범위는 별도 논의 필요.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
