package com.memorin.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.test.context.DynamicPropertyRegistry;

// 쿼리 수 테스트용 카운터. 현재 스레드가 실행한 SQL만 센다. (#290)
//
// Hibernate Statistics를 쓰지 않는 이유:
//  - Statistics는 SessionFactory 전역 값이라 다른 스레드의 쿼리까지 합쳐서 센다.
//  - @Scheduled 정리 잡(DeletedMediaCleanupJob, PendingUploadCleanupJob)은 컨텍스트가 뜨자마자
//    백그라운드 스레드에서 한 번 돈다. 그 쿼리가 첫 테스트의 측정 구간에 걸리면 한쪽만 1~3개
//    늘어 N+1 비교가 간헐적으로 깨진다.
//    실측: RecommendedFeedQueryTest가 "미디어 5장 → SQL 5개 / 15장 → SQL 4개"로 실패했다.
//  - @Async 알림 리스너도 같은 식으로 섞일 수 있다.
//
// 측정 대상 서비스 호출은 테스트 스레드에서 동기로 실행되므로 ThreadLocal로 세면 그 호출의
// 쿼리만 남는다.
//
// 사용법: @DynamicPropertySource에서 register()를 부르고, 측정 구간을 reset()과 count()로 감싼다.
public class QueryCountInspector implements StatementInspector {

    private static final ThreadLocal<long[]> COUNT = ThreadLocal.withInitial(() -> new long[1]);

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                QueryCountInspector.class::getName);
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static long count() {
        return COUNT.get()[0];
    }

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }
}
