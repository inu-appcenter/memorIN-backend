package com.memorin.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 실제 PostgreSQL 컨테이너를 띄워 JPA 테스트를 수행하는 공통 베이스.
//
// H2를 쓰지 않는 이유:
//  - posts.content가 jsonb 컬럼이다.
//  - PostRepository.findUserFeed가 Postgres 전용 행 비교((a,b) < (c,d)) 커서 페이징을 쓴다.
//
// 스키마는 운영과 동일하게 Flyway(src/main/resources/db/migration)로 만든다. 컨테이너가 뜨면
// Spring Boot가 자동으로 마이그레이션을 실행하므로 여기서 따로 초기화 SQL을 넣을 필요가 없다.
// ddl-auto=validate로 엔티티가 실제 배포되는 스키마와 어긋나지 않는지도 함께 검증한다.
@Testcontainers
public abstract class PostgresTestSupport {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
