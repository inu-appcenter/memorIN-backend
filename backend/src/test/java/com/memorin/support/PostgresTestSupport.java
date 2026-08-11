package com.memorin.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

// 실제 PostgreSQL 컨테이너를 띄워 JPA 테스트를 수행하는 공통 베이스.
//
// H2를 쓰지 않는 이유:
//  - posts.content가 jsonb 컬럼이다.
//  - PostRepository.findUserFeed가 Postgres 전용 행 비교((a,b) < (c,d)) 커서 페이징을 쓴다.
//
// 스키마는 운영과 동일하게 Flyway(src/main/resources/db/migration)로 만든다. 컨테이너가 뜨면
// Spring Boot가 자동으로 마이그레이션을 실행하므로 여기서 따로 초기화 SQL을 넣을 필요가 없다.
// ddl-auto=validate로 엔티티가 실제 배포되는 스키마와 어긋나지 않는지도 함께 검증한다.
//
// 컨테이너 수명 — @Testcontainers/@Container 대신 static 블록에서 직접 start() 한다.
// (Testcontainers 공식 싱글턴 컨테이너 패턴)
//
// @Container를 쓰면 JUnit이 "테스트 클래스마다" 컨테이너를 start/stop 한다. 그런데 이 필드는
// static이라 모든 하위 클래스가 하나를 공유하므로, 먼저 끝난 클래스가 컨테이너를 내려버린다.
// Spring은 컨텍스트를 캐시해 재사용하는데 그 컨텍스트의 DataSource는 이미 사라진 포트를 가리켜
// "Connection is not available, request timed out (total=0)"으로 실패한다.
// 실측: 테스트 클래스를 하나 추가하자 무관한 기존 클래스가 이 오류로 깨졌다.
//
// static 블록에서 start()만 하면 컨테이너는 JVM이 끝날 때까지 살아 있고(Ryuk이 정리),
// 클래스 수와 무관하게 안정적이다. 전체 실행도 컨테이너를 한 번만 띄우므로 더 빠르다.
public abstract class PostgresTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
