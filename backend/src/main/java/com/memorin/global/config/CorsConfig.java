package com.memorin.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// CORS(Cross-Origin Resource Sharing) 설정.
// 브라우저가 다른 오리진(도메인)의 API 요청을 허용받기 위한 규칙을 정의한다.
//
// 허용 오리진은 환경별 프로퍼티(cors.allowed-origins)에서 주입한다.
//   - dev  : application.properties        → localhost 개발 서버
//   - prod : application-docker.properties  → 실제 배포 도메인(CORS_ALLOWED_ORIGINS 환경변수)
//
// 주의: CORS는 "브라우저"에서만 동작하는 보호 장치다.
//       네이티브 앱(RN/Expo) 요청은 이 목록과 무관하게 통과한다. (웹/WebView일 때만 의미 있음)
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    // 콤마로 구분된 문자열이 List<String>으로 자동 변환되어 주입된다.
    public CorsConfig(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);                                       // 환경별 허용 도메인
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));                                         // Authorization 등 모든 요청 헤더 허용
        config.setAllowCredentials(false);                                              // JWT는 Authorization 헤더로 전달(쿠키 미사용).
                                                                                        // 추후 쿠키 기반 인증 도입 시 true + 허용 헤더 명시로 변경.
        config.setMaxAge(3600L);                                                        // preflight(OPTIONS) 결과 1시간 캐시

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);                               // 모든 경로에 적용
        return source;
    }
}
