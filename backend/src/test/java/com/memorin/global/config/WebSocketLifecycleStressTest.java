package com.memorin.global.config;

import com.memorin.support.PostgresTestSupport;
import com.memorin.support.TcpProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

// WebSocket 생명주기 스트레스 테스트 (docs/sprint4-architecture-review.md §6).
//
// 게이트 문구는 "탭 닫기 시 InMemory 세션 즉각 반환"이다. 이걸 숫자로 확인한다.
// 결과는 docs/ws-stress-test.md에 기록한다.
//
// CI에서는 돌지 않는다(@Tag("stress") → test 태스크에서 제외). 실행:
//   JWT_SECRET=... ./gradlew stressTest
//
// 왜 JMeter/Gatling이 아니라 JUnit인가 — 세션 수와 힙을 서버와 같은 JVM에서 바로 읽을 수 있기 때문이다.
// 외부 도구로는 "클라이언트가 몇 개 붙었나"만 알 수 있고, 정작 알고 싶은 "서버가 몇 개를 들고 있나"는 못 본다.
@Tag("stress")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WebSocketLifecycleStressTest extends PostgresTestSupport {

    private static final Duration HEARTBEAT_GRACE = Duration.ofSeconds(60);   // 하트비트 10초 × 여유
    private static final long[] CLIENT_HEARTBEAT_MS = {10_000, 10_000};

    @LocalServerPort
    private int port;

    @Autowired
    private WebSocketSessionRegistry sessionRegistry;

    @Autowired
    private WebSocketMessageBrokerStats brokerStats;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler clientScheduler;
    private final List<StompSession> openedSessions = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        clientScheduler = new ThreadPoolTaskScheduler();
        clientScheduler.setPoolSize(2);
        clientScheduler.setThreadNamePrefix("stomp-client-");
        clientScheduler.initialize();

        // 클라이언트 쪽 프레임 버퍼 기본값은 8KB다. 브로드캐스트 시나리오에서 32KB를 보내므로
        // 여기를 올려두지 않으면 서버가 아니라 테스트 클라이언트가 1009(TOO_BIG)로 연결을 끊는다.
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient(container));
        // 클라이언트가 하트비트에 응하지 않으면(0,0) 서버는 죽은 연결을 영영 못 잡는다.
        // 브라우저 stomp.js 기본값과 같은 10초/10초로 맞춘다.
        stompClient.setDefaultHeartbeat(CLIENT_HEARTBEAT_MS);
        stompClient.setTaskScheduler(clientScheduler);

        // 앞 시나리오의 잔재가 없는 상태에서 시작한다. 하트비트로만 회수되는 세션이 남았을 수 있어
        // 회수 상한(≈37초)보다 넉넉히 기다린다.
        awaitSessionCount(0, Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
        // 실패한 테스트가 세션을 남기면 다음 테스트가 연쇄로 깨진다. 여기서 확실히 닫는다.
        synchronized (openedSessions) {
            for (StompSession session : openedSessions) {
                try {
                    if (session.isConnected()) {
                        session.disconnect();
                    }
                } catch (RuntimeException ignored) {
                    // 이미 끊긴 세션
                }
            }
            openedSessions.clear();
        }
        stompClient.stop();
        clientScheduler.shutdown();
    }

    @Test
    void 정상_종료된_세션은_즉시_반환된다() {
        List<StompSession> sessions = connect(50, port);
        assertThat(sessionRegistry.activeCount()).isEqualTo(50);

        sessions.forEach(StompSession::disconnect);
        long elapsedMs = awaitSessionCount(0, Duration.ofSeconds(10));

        report("정상 종료 (DISCONNECT)", 50, elapsedMs);
    }

    @Test
    void 소켓이_강제로_끊긴_세션도_반환된다() throws IOException {
        try (TcpProxy proxy = new TcpProxy(port)) {
            connect(20, proxy.port());
            assertThat(sessionRegistry.activeCount()).isEqualTo(20);

            proxy.reset();   // FIN 없이 RST
            long elapsedMs = awaitSessionCount(0, Duration.ofSeconds(30));

            report("비정상 종료 (RST)", 20, elapsedMs);
        }
    }

    @Test
    void 응답이_끊긴_세션은_하트비트로_회수된다() throws IOException {
        try (TcpProxy proxy = new TcpProxy(port)) {
            connect(10, proxy.port());
            assertThat(sessionRegistry.activeCount()).isEqualTo(10);

            // 소켓은 열려 있는데 바이트만 흐르지 않는다. 커널이 알려줄 것이 없으므로
            // 하트비트 타임아웃 외에는 서버가 이 세션이 죽은 줄 알 방법이 없다.
            proxy.freeze();
            long elapsedMs = awaitSessionCount(0, HEARTBEAT_GRACE);

            report("무응답 (블랙홀)", 10, elapsedMs);
            // 하트비트가 꺼져 있으면 여기까지 오지 못하고 타임아웃으로 실패한다.
            assertThat(elapsedMs).isLessThan(HEARTBEAT_GRACE.toMillis());
        }
    }

    @Test
    void 브로드캐스트_지연을_측정한다() {
        String destination = "/topic/rooms/" + UUID.randomUUID();
        int subscribers = 30;
        int messages = 200;

        List<StompSession> sessions = connect(subscribers, port);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>());
        sessions.forEach(session -> session.subscribe(destination, latencyRecorder(latenciesNanos)));

        // SUBSCRIBE는 비동기라 구독 등록 전에 보낸 메시지는 그냥 사라진다.
        // 워밍업 한 발을 쏴서 전원이 받는 것을 확인한 뒤에 측정을 시작한다.
        awaitUntil(Duration.ofSeconds(10), () -> {
            messagingTemplate.convertAndSend(destination, payloadWithTimestamp());
            return latenciesNanos.size() >= subscribers;
        });
        latenciesNanos.clear();

        for (int i = 0; i < messages; i++) {
            messagingTemplate.convertAndSend(destination, payloadWithTimestamp());
        }

        int expected = subscribers * messages;
        boolean allReceived = awaitUntil(Duration.ofSeconds(30), () -> latenciesNanos.size() >= expected);

        long p95Ms = percentileMillis(latenciesNanos, 95);
        System.out.printf("[stress] 브로드캐스트 — 구독자 %d × %d건 = %d, 수신 %d, 지연 p95 %dms%n",
            subscribers, messages, expected, latenciesNanos.size(), p95Ms);

        sessions.forEach(StompSession::disconnect);
        assertThat(allReceived).as("전원이 전부 수신").isTrue();
    }

    @Test
    void 느린_수신자는_격리되고_나머지는_계속_받는다() throws IOException {
        String destination = "/topic/rooms/" + UUID.randomUUID();
        // 구독자를 적게 잡는다. 클라이언트 11개가 전부 같은 JVM에 있어서, 수를 늘리면
        // 서버가 아니라 테스트 클라이언트가 먼저 병목이 된다(정상 세션까지 전송 상한에 걸린다).
        int healthy = 3;

        try (TcpProxy proxy = new TcpProxy(port)) {
            List<StompSession> healthySessions = connect(healthy, port);
            StompSession stalled = connect(1, proxy.port()).get(0);   // 프록시를 거치는 한 명

            AtomicInteger received = new AtomicInteger();
            healthySessions.forEach(session -> session.subscribe(destination, counter(received)));
            AtomicInteger stalledReceived = new AtomicInteger();
            stalled.subscribe(destination, counter(stalledReceived));
            assertThat(sessionRegistry.activeCount()).isEqualTo(healthy + 1);

            // 구독 등록이 실제로 끝났는지 확인하고 시작한다.
            awaitUntil(Duration.ofSeconds(10), () -> {
                messagingTemplate.convertAndSend(destination, new byte[]{0});
                return received.get() >= healthy && stalledReceived.get() >= 1;
            });
            received.set(0);

            // 한 명만 수신이 멎는다(읽지 않으므로 TCP 윈도가 닫힌다). 이후 전송분은 그 세션의 버퍼에 쌓인다.
            proxy.stall();

            // 세션당 버퍼 상한(512KB)을 확실히 넘긴다: 32KB × 60 = 1.9MB.
            // 정상 구독자가 소화할 시간을 주려고 간격을 둔다 — 멎은 세션은 어차피 계속 쌓인다.
            byte[] chunk = new byte[32 * 1024];
            for (int i = 0; i < 60; i++) {
                messagingTemplate.convertAndSend(destination, chunk);
                sleep(20);
            }

            int expected = healthy * 60;
            boolean healthyUnaffected = awaitUntil(Duration.ofSeconds(30), () -> received.get() >= expected);
            // 버퍼/전송시간 상한에 걸린 세션은 서버가 끊는다. 하트비트 타임아웃(≈37초)보다 먼저 와야 한다.
            boolean stalledDropped = awaitUntil(Duration.ofSeconds(30),
                () -> sessionRegistry.activeCount() <= healthy);

            System.out.printf("[stress] 느린 수신자 — 정상 %d명 수신 %d/%d, 활성 세션 %d, %s%n",
                healthy, received.get(), expected, sessionRegistry.activeCount(),
                brokerStats.getWebSocketSessionStatsInfo());

            healthySessions.forEach(StompSession::disconnect);
            assertThat(healthyUnaffected).as("느린 수신자와 무관하게 정상 구독자는 전부 수신").isTrue();
            assertThat(stalledDropped).as("상한에 걸린 세션은 서버가 정리").isTrue();
        }
    }

    @Test
    void 연결과_해제를_반복해도_힙이_증가하지_않는다() {
        long before = usedHeapAfterGc();

        for (int round = 0; round < 10; round++) {
            List<StompSession> sessions = connect(10, port);
            sessions.forEach(StompSession::disconnect);
            awaitSessionCount(0, Duration.ofSeconds(10));
        }

        long after = usedHeapAfterGc();
        long deltaMb = (after - before) / (1024 * 1024);

        System.out.printf("[stress] 연결·해제 100회 — 힙 %dMB → %dMB (Δ %+dMB), %s%n",
            before / (1024 * 1024), after / (1024 * 1024), deltaMb,
            brokerStats.getWebSocketSessionStatsInfo());

        assertThat(sessionRegistry.activeCount()).isZero();
        // 세션 100개가 통째로 남아 있으면 이 폭을 넘는다. GC 타이밍 흔들림은 흡수한다.
        assertThat(deltaMb).isLessThan(32);
    }

    private List<StompSession> connect(int count, int targetPort) {
        String url = "ws://127.0.0.1:" + targetPort + "/ws/websocket";
        int baseline = sessionRegistry.activeCount();   // 이미 붙어 있는 세션이 있을 수 있다
        List<StompSession> sessions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            try {
                sessions.add(stompClient
                    .connectAsync(url, new StompSessionHandlerAdapter() { })
                    .get(15, TimeUnit.SECONDS));
            } catch (Exception e) {
                throw new IllegalStateException(
                    "STOMP 연결 실패 (%d/%d번째, url=%s)".formatted(i + 1, count, url), e);
            }
        }
        awaitSessionCount(baseline + count, Duration.ofSeconds(15));
        openedSessions.addAll(sessions);
        return sessions;
    }

    private long awaitSessionCount(int expected, Duration timeout) {
        long startNanos = System.nanoTime();
        boolean reached = awaitUntil(timeout, () -> sessionRegistry.activeCount() == expected);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        if (!reached) {
            throw new AssertionError(
                "%d초 안에 세션 수가 %d로 오지 않았다. 현재=%d, %s".formatted(
                    timeout.toSeconds(), expected, sessionRegistry.activeCount(),
                    brokerStats.getWebSocketSessionStatsInfo()));
        }
        return elapsedMs;
    }

    private boolean awaitUntil(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private byte[] payloadWithTimestamp() {
        return String.valueOf(System.nanoTime()).getBytes(StandardCharsets.UTF_8);
    }

    // 서버와 같은 JVM이라 nanoTime을 그대로 뺄 수 있다. 외부 부하 도구로는 못 하는 측정이다.
    private StompFrameHandler latencyRecorder(List<Long> latenciesNanos) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                long sentAt = Long.parseLong(new String((byte[]) payload, StandardCharsets.UTF_8));
                latenciesNanos.add(System.nanoTime() - sentAt);
            }
        };
    }

    private StompFrameHandler counter(AtomicInteger counter) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                counter.incrementAndGet();
            }
        };
    }

    private long percentileMillis(List<Long> samplesNanos, int percentile) {
        if (samplesNanos.isEmpty()) {
            return -1;
        }
        List<Long> sorted = new ArrayList<>(samplesNanos);
        Collections.sort(sorted);
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile / 100.0) - 1);
        return sorted.get(index) / 1_000_000;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long usedHeapAfterGc() {
        // 한 번의 gc()는 힌트일 뿐이라 두 번 부르고 잠깐 기다린다. 절대값이 아니라 증분을 본다.
        for (int i = 0; i < 2; i++) {
            System.gc();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private void report(String scenario, int sessions, long elapsedMs) {
        System.out.printf("[stress] %s — 세션 %d개 회수까지 %dms, %s%n",
            scenario, sessions, elapsedMs, brokerStats.getWebSocketSessionStatsInfo());
    }
}
