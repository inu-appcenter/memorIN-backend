package com.memorin.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// OpenAPI 문서가 인증 없이 열리고, 실제 컨트롤러가 명세에 잡히는지 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OpenApiDocsTest extends PostgresTestSupport {

    // HTTP 메서드 키만 골라내기 위한 화이트리스트. paths 아래에는 parameters 같은 비-메서드 키도 올 수 있다.
    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    // 이 테스트가 없으면 새 컨트롤러가 @Operation 없이 머지돼도 아무도 모른다.
    // 실제로 이모지 API 3개와 팔로우 요청 목록이 그렇게 스프린트 두 개를 넘어갔다.
    @Test
    void 모든_엔드포인트에_summary가_붙어_있다() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> op : path.getValue().properties()) {
                if (!HTTP_METHODS.contains(op.getKey())) {
                    continue;
                }
                String summary = op.getValue().path("summary").asText("");
                if (summary.isBlank()) {
                    missing.add(op.getKey().toUpperCase() + " " + path.getKey());
                }
            }
        }

        assertThat(missing)
                .as("@Operation(summary=...) 누락 — Swagger 목록에 메서드명이 그대로 노출된다")
                .isEmpty();
    }

    // 그룹(@Tag)이 없으면 Swagger UI에서 default 묶음으로 떨어져 FE가 찾지 못한다.
    @Test
    void 모든_엔드포인트가_태그로_분류돼_있다() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        List<String> untagged = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> op : path.getValue().properties()) {
                if (!HTTP_METHODS.contains(op.getKey())) {
                    continue;
                }
                JsonNode tags = op.getValue().path("tags");
                // @Tag가 없으면 springdoc이 "xxx-controller"를 태그로 자동 생성한다. 그것도 누락으로 본다.
                boolean tagged = tags.isArray() && !tags.isEmpty()
                        && !tags.get(0).asText("").endsWith("-controller");
                if (!tagged) {
                    untagged.add(op.getKey().toUpperCase() + " " + path.getKey());
                }
            }
        }

        assertThat(untagged)
                .as("@Tag 누락 — 컨트롤러 클래스명이 그대로 그룹명이 된다")
                .isEmpty();
    }

    private JsonNode apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
