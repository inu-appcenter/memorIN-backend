package com.memorin.global.config;

import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.domain.messages.dto.request.TextRequest;
import com.memorin.domain.messages.repository.MessageRepository;
import com.memorin.domain.messages.service.MessageService;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.support.PostgresTestSupport;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HikariCP 커넥션 풀 부하 실측 (#231).
 *
 * <p><b>왜 재야 하나</b> — 현재 풀 설정은 실측 없이 잡힌 출발점이다.
 *
 * <pre>
 * spring.datasource.hikari.maximum-pool-size=10
 * spring.datasource.hikari.minimum-idle=10
 * spring.datasource.hikari.connection-timeout=30000
 * </pre>
 *
 * <p>그리고 Sprint 4에서 서비스 성격이 바뀌었다. 채팅은 메시지마다 방 멤버 검사 + INSERT가
 * 동기로 일어난다({@code docs/ws-stress-test.md}). 동시 발신자가 풀 크기보다 많으면
 * <b>커넥션 대기가 그대로 메시지 지연이 된다.</b> 지금까지의 측정은 발신자가 1명이라
 * 이 구간을 한 번도 밟지 않았다.
 *
 * <p><b>무엇을 재나</b>
 * <ul>
 *   <li>동시 발신자 N명일 때 {@code sendText} 호출 지연 p50 / p95 / max</li>
 *   <li>커넥션 <b>획득</b> 대기 — 별도 프로브 스레드가 {@code getConnection()}만 따로 잰다.
 *       요청 지연에는 쿼리 시간도 섞이므로 대기만 떼어 봐야 풀이 병목인지 알 수 있다</li>
 *   <li>{@code threadsAwaitingConnection} 최대값 — 풀 앞에 줄이 얼마나 서는가</li>
 *   <li>풀 타임아웃 발생 건수와 메시지 유실 여부</li>
 * </ul>
 *
 * <p><b>실행</b>
 * <pre>
 * JWT_SECRET=... ./gradlew stressTest --tests '*HikariPoolLoadTest*' \
 *     -Dstress.senders=30 -Dstress.hikari.pool=10
 * </pre>
 *
 * <p><b>하네스의 한계</b> — 서버·클라이언트·DB 컨테이너가 한 머신에 있다. 절대 처리량은
 * 서버 한계가 아니다. 의미가 있는 것은 <b>같은 머신에서 풀 크기만 바꿨을 때의 변화</b>다.
 */
@Tag("stress")
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HikariPoolLoadTest extends PostgresTestSupport {

    private static final int SENDERS = Integer.getInteger("stress.senders", 30);
    private static final int MESSAGES_PER_SENDER = Integer.getInteger("stress.messages", 40);
    private static final String POOL_SIZE = System.getProperty("stress.hikari.pool", "10");

    @DynamicPropertySource
    static void poolProperties(DynamicPropertyRegistry registry) {
        // application-docker.properties와 같은 구성을 테스트 컨텍스트에 재현한다.
        // 테스트 기본 프로파일은 풀 설정이 없어 HikariCP 기본값으로 돌기 때문에,
        // 명시하지 않으면 "운영과 같은 조건"이 아니게 된다.
        registry.add("spring.datasource.hikari.pool-name", () -> "memorin-pool");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> POOL_SIZE);
        registry.add("spring.datasource.hikari.minimum-idle", () -> POOL_SIZE);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private HikariPoolMXBean pool() {
        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
    }

    @Test
    void 동시_발신자가_늘어날_때_커넥션_풀이_병목인지_측정한다() throws Exception {
        // ── given: 발신자 전원이 같은 방의 활성 멤버여야 MessageService의 멤버 검사를 통과한다
        UUID[] senderIds = new UUID[SENDERS];
        UUID roomId = tx.execute(status -> {
            String tag = "hikari" + UUID.randomUUID().toString().substring(0, 6);
            User owner = userRepository.save(
                new User(tag + "-o@memorin.test", "hash", tag + "-o", tag + "-o", null));
            List<UUID> memberIds = new ArrayList<>();
            for (int i = 0; i < SENDERS; i++) {
                User u = userRepository.save(new User(
                    "%s-%d@memorin.test".formatted(tag, i), "hash",
                    "%s-%d".formatted(tag, i), "%s-%d".formatted(tag, i), null));
                senderIds[i] = u.getId();
                memberIds.add(u.getId());
            }
            em.flush();
            ChatRoomResponse room = chatRoomService.createGroupRoom(
                owner.getId(), new CreateGroupRoomRequest("hikari-" + tag, memberIds));
            return room.roomId();
        });

        long savedBefore = messageRepository.count();

        // ── 풀 상태 샘플러: 줄이 가장 길어진 순간을 잡는다
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger maxAwaiting = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        Thread sampler = new Thread(() -> {
            while (running.get()) {
                maxAwaiting.accumulateAndGet(pool().getThreadsAwaitingConnection(), Math::max);
                maxActive.accumulateAndGet(pool().getActiveConnections(), Math::max);
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "hikari-sampler");
        sampler.setDaemon(true);

        // ── 획득 프로브: getConnection()만 따로 잰다.
        // 요청 지연에는 쿼리 시간이 섞이므로, 풀이 병목인지는 이 값으로 봐야 한다.
        List<Long> acquireNanos = Collections.synchronizedList(new ArrayList<>());
        Thread probe = new Thread(() -> {
            while (running.get()) {
                long t0 = System.nanoTime();
                try (Connection c = dataSource.getConnection()) {
                    acquireNanos.add(System.nanoTime() - t0);
                } catch (Exception e) {
                    acquireNanos.add(Long.MAX_VALUE);   // 타임아웃도 표본에 남긴다
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "hikari-probe");
        probe.setDaemon(true);

        // ── when
        List<Long> sendNanos = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(SENDERS);
        CountDownLatch ready = new CountDownLatch(SENDERS);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(SENDERS);

        for (int i = 0; i < SENDERS; i++) {
            UUID senderId = senderIds[i];
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    for (int m = 0; m < MESSAGES_PER_SENDER; m++) {
                        long t0 = System.nanoTime();
                        try {
                            messageService.sendText(senderId, new TextRequest(roomId, "load-" + m));
                            sendNanos.add(System.nanoTime() - t0);
                        } catch (RuntimeException e) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).as("발신 스레드 준비").isTrue();
        sampler.start();
        probe.start();

        long startNanos = System.nanoTime();
        go.countDown();
        boolean finished = done.await(180, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        running.set(false);
        pool.shutdownNow();

        // ── then
        int expected = SENDERS * MESSAGES_PER_SENDER;
        long savedAfter = messageRepository.count();

        System.out.printf("%n" +
                ">>> [hikari] 풀 %s / 동시 발신자 %d명 × %d건 = %d%n" +
                ">>> [hikari] 발신 지연      p50 %dms · p95 %dms · max %dms%n" +
                ">>> [hikari] 커넥션 획득    p50 %dms · p95 %dms · max %dms (표본 %d)%n" +
                ">>> [hikari] 풀 상태        대기 스레드 최대 %d · 활성 커넥션 최대 %d%n" +
                ">>> [hikari] 처리량         %d건 / %dms = %d msg/s%n" +
                ">>> [hikari] 실패 %d건 · DB 저장 %d건%n%n",
            POOL_SIZE, SENDERS, MESSAGES_PER_SENDER, expected,
            millis(sendNanos, 50), millis(sendNanos, 95), millis(sendNanos, 100),
            millis(acquireNanos, 50), millis(acquireNanos, 95), millis(acquireNanos, 100),
            acquireNanos.size(),
            maxAwaiting.get(), maxActive.get(),
            sendNanos.size(), elapsedMs,
            elapsedMs == 0 ? 0 : sendNanos.size() * 1000L / elapsedMs,
            failures.get(), savedAfter - savedBefore);

        assertThat(finished).as("180초 안에 전부 끝난다").isTrue();
        assertThat(failures.get()).as("풀 타임아웃 등으로 실패한 발신이 없어야 한다").isZero();
        assertThat(savedAfter - savedBefore)
            .as("보낸 만큼 저장돼야 한다 — 유실이 있으면 풀 문제가 아니라 트랜잭션 문제다")
            .isEqualTo(expected);
    }

    /**
     * 풀 크기와 PostgreSQL {@code max_connections}의 관계를 기록한다.
     *
     * <p>풀을 키워도 DB가 못 받으면 의미가 없다. 인스턴스가 여러 대면
     * {@code 인스턴스 수 × maximum-pool-size}가 {@code max_connections} 안에 들어와야 한다.
     */
    @Test
    void PostgreSQL_최대_커넥션과_풀_크기의_관계를_기록한다() throws Exception {
        int maxConnections;
        int superuserReserved;
        try (Connection c = dataSource.getConnection();
             var st = c.createStatement()) {
            maxConnections = readIntSetting(st, "max_connections");
            superuserReserved = readIntSetting(st, "superuser_reserved_connections");
        }

        int poolSize = Integer.parseInt(POOL_SIZE);
        int usable = maxConnections - superuserReserved;

        System.out.printf("%n" +
                ">>> [hikari] PostgreSQL max_connections %d (superuser 예약 %d) → 애플리케이션이 쓸 수 있는 것 %d%n" +
                ">>> [hikari] 현재 풀 %d → 인스턴스 %d대까지 수용 가능%n%n",
            maxConnections, superuserReserved, usable, poolSize, usable / poolSize);

        assertThat(poolSize)
            .as("풀 하나가 DB 상한을 넘으면 기동하자마자 커넥션을 못 얻는다")
            .isLessThan(usable);
    }

    private static int readIntSetting(java.sql.Statement st, String name) throws Exception {
        try (var rs = st.executeQuery("SHOW " + name)) {
            rs.next();
            return Integer.parseInt(rs.getString(1));
        }
    }

    /** 표본이 비면 0. {@code percentile=100}이면 최대값. */
    private static long millis(List<Long> nanos, int percentile) {
        List<Long> sorted;
        synchronized (nanos) {
            if (nanos.isEmpty()) return 0;
            sorted = new ArrayList<>(nanos);
        }
        Collections.sort(sorted);
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile / 100.0) - 1);
        long value = sorted.get(Math.max(0, idx));
        return value == Long.MAX_VALUE ? -1 : value / 1_000_000;
    }
}
