package com.memorin.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI: http://localhost:8080/swagger-ui.html
// OpenAPI 문서: http://localhost:8080/v3/api-docs
//
// bearerAuth를 전역 보안 스키마로 등록해 Swagger UI의 Authorize 버튼에
// accessToken을 한 번 넣으면 모든 요청에 Authorization 헤더가 붙는다.
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI memorinOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("memorIN API")
                        .description("""
                                memorIN 백엔드 API 명세.

                                인증이 필요한 API는 우측 상단 Authorize에 `/auth/login`으로 받은
                                accessToken을 입력한 뒤 호출한다. (Bearer 접두사는 자동으로 붙는다)
                                """)
                        .version("v0.0.1"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }
}
