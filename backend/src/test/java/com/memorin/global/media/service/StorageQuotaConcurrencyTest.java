package com.memorin.global.media.service;

import com.memorin.domain.pending_upload.repository.PendingUploadRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.global.media.exception.StorageQuotaExceededException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 이슈 #70 P1 회귀 테스트: 잔여 100MB 한도에 90MB 예약 2건이 동시에 들어오면
// 하나만 성공하고 하나는 StorageQuotaExceededException이어야 한다 (기존엔 둘 다 통과해 180MB가 됐다).
// 실제 동시성/행 잠금을 검증해야 하므로 Mockito가 아니라 진짜 Postgres 트랜잭션 2개를 띄운다.
//
// PostgresTestSupport를 상속하지 않고 독립 컨테이너를 쓴다 (그 클래스의 static 필드를 여러
// 테스트 클래스가 상속하면 JUnit5 Testcontainers 확장이 클래스마다 afterAll에서 같은 인스턴스를
// stop시켜, 전체 스위트 실행 시 나중에 도는 클래스가 죽은 컨테이너에 연결하려다 실패하는 문제가 있다).
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StorageQuotaConcurrencyTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 스키마는 Flyway(src/main/resources/db/migration)가 만든다.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // 이 테스트의 한도(1000L)만큼은 애플리케이션 기본 설정과 무관하게 고정해야 시나리오가 안정적이다.
        registry.add("storage.quota.default-limit-bytes", () -> "1000");
    }

    @Autowired
    private StorageQuotaService storageQuotaService;

    @Autowired
    private PendingUploadRepository pendingUploadRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private UUID seedUser(String suffix) {
        return tx.execute(status -> {
            User user = new User(suffix + "@memorin.test", "hash", suffix, suffix, null);
            em.persist(user);
            em.flush();
            return user.getId();
        });
    }

    @Test
    void 잔여_100MB에_90MB_2건_동시_요청시_하나만_성공한다() throws Exception {
        // given: 한도 1000, 기존 사용량 0 -> 900짜리 예약 2건이 동시에 들어오면 하나는 넘친다 (900+900=1800 > 1000).
        UUID userId = seedUser("toctou");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Boolean>> futures = List.of(
                    pool.submit(() -> attemptReserve(userId, "uploads/a.png", 900L, ready, go)),
                    pool.submit(() -> attemptReserve(userId, "uploads/b.png", 900L, ready, go))
            );

            ready.await(5, TimeUnit.SECONDS);
            go.countDown(); // 두 스레드가 최대한 같은 순간에 reserveUpload를 호출하도록 동시에 풀어준다.

            long successCount = futures.stream().map(this::getQuietly).filter(Boolean::booleanValue).count();

            // then: 정확히 하나만 성공해야 한다. TOCTOU가 있었다면 둘 다 성공(180MB 상당 예약)했을 것이다.
            assertThat(successCount).isEqualTo(1);
            // 서비스가 expiresAt을 Clock.systemUTC() 기준으로 저장하므로 비교도 같은 기준으로 해야 한다.
            // (LocalDateTime은 타임존 정보가 없어서, 로컬 타임존 now()와 섞어 쓰면 KST 같은 UTC+N
            //  환경에서 어긋난다 - 이 프로젝트 코드 자체는 Clock을 일관되게 주입받아 써서 문제 없다.)
            assertThat(pendingUploadRepository.sumReservedBytesByUserId(
                    userId, java.time.LocalDateTime.now(java.time.Clock.systemUTC())))
                    .isEqualTo(900L);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean attemptReserve(UUID userId, String objectKey, long bytes, CountDownLatch ready, CountDownLatch go) {
        try {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            storageQuotaService.reserveUpload(userId, objectKey, bytes);
            return true;
        } catch (StorageQuotaExceededException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private boolean getQuietly(Future<Boolean> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
