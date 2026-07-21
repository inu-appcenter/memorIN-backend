package com.memorin.global.config;

import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// OpenAPI 문서가 인증 없이 열리고, 실제 컨트롤러가 명세에 잡히는지 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OpenApiDocsTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void api_docs는_인증_없이_열리고_등록된_API가_포함된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("memorIN API"))
                // 인증/게시물/미디어 컨트롤러가 실제로 문서에 잡히는지
                .andExpect(jsonPath("$.paths['/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/posts']").exists())
                .andExpect(jsonPath("$.paths['/api/media/presigned-upload-url']").exists())
                // Authorize 버튼이 동작하려면 bearerAuth 스키마가 있어야 한다
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
