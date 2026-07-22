package com.memorin.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

// 실제 PostgreSQL 컨테이너를 띄워 JPA 테스트를 수행하는 공통 베이스.
//
// H2를 쓰지 않는 이유:
//  - posts.content가 jsonb 컬럼이다.
//  - PostRepository.findUserFeed가 Postgres 전용 행 비교((a,b) < (c,d)) 커서 페이징을 쓴다.
//
// 스키마는 hibernate ddl-auto가 아니라 운영과 동일한 infra/postgres/init/01_init.sql로 만든다.
// 엔티티에서 생성한 스키마가 아니라 실제 배포되는 스키마와의 정합성을 검증하기 위해서다.
@Testcontainers
public abstract class PostgresTestSupport {

    private static final String INIT_SQL = "../infra/postgres/init/01_init.sql";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(INIT_SQL),
                    "/docker-entrypoint-initdb.d/01_init.sql"
            );

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // 스키마는 위 init 스크립트가 이미 만들었으므로 hibernate는 손대지 않는다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}
