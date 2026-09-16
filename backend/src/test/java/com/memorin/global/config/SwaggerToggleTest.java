package com.memorin.global.config;

import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #261: docker(배포) 프로파일에서 SWAGGER_ENABLED=false(기본값)일 때 실제로 문서가 닫히는지 확인한다.
// application-docker.properties의 springdoc.*.enabled를 직접 흉내내 이 프로퍼티만 끈 컨텍스트를
// 별도로 띄운다 — OpenApiDocsTest(기본 프로파일, 항상 켜짐)와는 다른 SpringBootTest 컨텍스트다.
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "springdoc.api-docs.enabled=false",
    "springdoc.swagger-ui.enabled=false"
})
class SwaggerToggleTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void springdoc이_꺼지면_api_docs가_200을_주지_않는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void springdoc이_꺼지면_swagger_ui가_200을_주지_않는다() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().is4xxClientError());
    }
}
