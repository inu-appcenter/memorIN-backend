package com.memorin.global.health.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

@Tag(name = "헬스체크", description = "인프라(Docker 등)용 상태 확인. 인증 불필요")
@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DataSource dataSource;

    @Operation(
        summary = "헬스체크",
        description = """
            DB 연결까지 확인한다 (#230). HTTP 계층은 응답하지만 DB 커넥션 풀이 죽어
            모든 요청이 타임아웃 나는 "반쯤 죽은" 상태를 잡기 위함이다 — 단순히 200을
            고정 반환하면 이 상태를 정상으로 오인한다.

            MinIO는 일부러 뺐다. 인증·게시물·댓글·팔로우 등 대부분 기능이 MinIO를 쓰지 않는데,
            MinIO 장애만으로 backend 전체가 unhealthy로 찍히면 오탐이 커진다. MinIO는 이미
            자체 헬스체크(docker-compose.yml)로 별도 관찰한다.""")
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
            log.warn("헬스체크 실패: DB 커넥션이 유효하지 않음");
        } catch (SQLException e) {
            log.warn("헬스체크 실패: DB 커넥션을 얻을 수 없음", e);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }
}
