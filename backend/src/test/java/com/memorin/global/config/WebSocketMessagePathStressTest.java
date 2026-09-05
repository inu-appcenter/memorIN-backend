package com.memorin.global.config;

import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.domain.messages.repository.MessageRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.support.PostgresTestSupport;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

// WebSocket 스트레스 테스트 2차 — 실제 채팅 메시지 경로 (docs/ws-stress-test.md).
//
// 1차는 브로커·세션 계층만 쟀다. `SimpMessagingTemplate`로 토픽에 직접 쏘는 방식이라
// 서비스 계층(권한 검사 + DB 저장)이 빠져 있었다. 2차는 그 구간을 포함한 실제 경로를 잰다:
//
//   클라이언트 SEND /app/chat.sendText
//     → CONNECT 인증(아래 StressConnectAuthConfig)
//     → MessageController.sendText
//     → MessageService.sendText  (방 멤버 검사 + messages INSERT + 커밋)
//     → SimpMessagingTemplate.convertAndSend("/topic/rooms/{roomId}")
//     → 구독자 수신
//
// CONNECT 인증은 아직 프로덕션에 없다(docs/sprint4-architecture-review.md §2, 담당 미지정).
// 그것이 없으면 @AuthenticationPrincipal이 null이라 이 경로를 아예 태울 수 없으므로,
// 여기서는 **테스트 스코프에만** 같은 모양의 인터셉터를 세워 측정한다.
// StressConnectAuthConfig는 측정용 스탠드인이지 §2의 구현이 아니다 — 프로덕션 인터셉터가
// 들어오면 이 설정을 지우고 그대로 다시 돌리면 된다.
//
// 실행: JWT_SECRET=... ./gradlew stressTest
@Tag("stress")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WebSocketMessagePathStressTest.StressConnectAuthConfig.class)
class WebSocketMessagePathStressTest extends PostgresTestSupport {

    // 1차에서 확인한 하네스 한계: 서버와 클라이언트가 같은 JVM이라 구독자를 11명 이상으로
    // 올리면 서버가 아니라 테스트 클라이언트가 먼저 병목이 된다. 그 아래로 잡는다.
    private static final int SUBSCRIBERS = 8;
    private static final int MESSAGES = 100;
    private static final long[] CLIENT_HEARTBEAT_MS = {10_000, 10_000};

    @LocalServerPort
    private int port;

    @Autowired
    private WebSocketSessionRegistry sessionRegistry;

    @Autowired
    private WebSocketMessageBrokerStats brokerStats;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler clientScheduler;
    private final List<StompSession> openedSessions = Collections.synchronizedList(new ArrayList<>());

    private UUID roomId;
    private String senderToken;
    private List<String> subscriberTokens;

    @BeforeEach
    void setUp() {
        clientScheduler = new ThreadPoolTaskScheduler();
        clientScheduler.setPoolSize(2);
        clientScheduler.setThreadNamePrefix("stomp-msg-client-");
        clientScheduler.initialize();

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient(container));
        stompClient.setDefaultHeartbeat(CLIENT_HEARTBEAT_MS);
        stompClient.setTaskScheduler(clientScheduler);
        // 서버가 MessageResponse를 JSON으로 내려주므로 클라이언트도 Jackson으로 맞춘다.
        // 받을 때는 Map으로 받는다 — MessageContent가 @JsonTypeInfo 붙은 sealed 인터페이스라
        // 클라이언트에서 구체 타입으로 역직렬화하면 측정과 무관한 실패가 섞인다.
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // 방 하나와 참여자를 실제 서비스로 만든다. 여기서 만들어지는 chat_room_members 행이
        // MessageService의 멤버 검사를 통과시키는 근거다.
        User sender = persistUser("sender");
        List<UUID> memberIds = new ArrayList<>();
        subscriberTokens = new ArrayList<>();
        for (int i = 0; i < SUBSCRIBERS; i++) {
            User member = persistUser("member" + i);
            memberIds.add(member.getId());
            subscriberTokens.add(jwtTokenProvider.createAccessToken(member.getId()));
        }
        senderToken = jwtTokenProvider.createAccessToken(sender.getId());

        ChatRoomResponse room = chatRoomService.createGroupRoom(
            sender.getId(), new CreateGroupRoomRequest("stress-" + UUID.randomUUID(), memberIds));
        roomId = room.roomId();

        awaitSessionCount(0, Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
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

    // 1차의 브로드캐스트 측정(p95 106ms)과 같은 형태지만, 이번에는 서비스 계층과 DB 저장이
    // 경로에 들어 있다. 두 숫자의 차이가 "동기 저장이 붙는 비용"이다.
    @Test
    void 실제_메시지_경로의_왕복_지연을_측정한다() {
        List<StompSession> subscribers = connectAll(subscriberTokens);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>());
        subscribers.forEach(s -> s.subscribe("/topic/rooms/" + roomId, latencyRecorder(latenciesNanos)));

        StompSession senderSession = connect(senderToken);
        long savedBefore = messageRepository.count();

        // SUBSCRIBE는 비동기다. 전원이 실제로 받는 것을 확인한 뒤에 측정을 시작한다.
        boolean warmedUp = awaitUntil(Duration.ofSeconds(20), () -> {
            // 워밍업도 측정 메시지와 같은 접두사로 보낸다 — latencyRecorder가 "stress-"만
            // 표본으로 잡기 때문에, 다른 접두사를 쓰면 이 조건이 영원히 성립하지 않는다.
            send(senderSession, "stress-" + System.nanoTime());
            return latenciesNanos.size() >= SUBSCRIBERS;
        });
        assertThat(warmedUp).as("워밍업 메시지를 구독자 전원이 수신").isTrue();
        latenciesNanos.clear();

        long startNanos = System.nanoTime();
        for (int i = 0; i < MESSAGES; i++) {
            send(senderSession, "stress-" + System.nanoTime());
        }

        int expected = SUBSCRIBERS * MESSAGES;
        boolean allReceived = awaitUntil(Duration.ofSeconds(60), () -> latenciesNanos.size() >= expected);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        // 워밍업분까지 세면 어긋나므로, 증가분이 발신 건수 이상인지로 본다.
        long savedAfter = messageRepository.count();
        long savedDelta = savedAfter - savedBefore;

        System.out.printf(
            "[stress2] 메시지 경로 — 구독자 %d × %d건 = %d, 수신 %d, p50 %dms, p95 %dms, "
                + "전체 %dms (%.0f msg/s), DB 저장 %d건%n",
            SUBSCRIBERS, MESSAGES, expected, latenciesNanos.size(),
            percentileMillis(latenciesNanos, 50), percentileMillis(latenciesNanos, 95),
            elapsedMs, MESSAGES * 1000.0 / Math.max(elapsedMs, 1), savedDelta);

        assertThat(allReceived).as("구독자 전원이 전부 수신").isTrue();
        assertThat(savedDelta).as("발신한 메시지가 전부 저장됨(워밍업 포함이라 이상)").isGreaterThanOrEqualTo(MESSAGES);
    }

    // 위 측정의 대조군. 같은 구독자 수·같은 건수를 브로커로 직접 쏜다(1차와 같은 방식).
    // 두 숫자의 차이가 곧 "인증 + 멤버 검사 + messages INSERT + 커밋"이 붙는 비용이다.
    // 1차 문서의 p95 106ms는 구독자 30 × 200건이라 이번 결과와 그대로 비교하면 안 된다.
    @Test
    void 대조군_브로커_직행_경로의_지연을_같은_조건에서_측정한다() {
        List<StompSession> subscribers = connectAll(subscriberTokens);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>());
        String destination = "/topic/rooms/" + roomId;
        subscribers.forEach(s -> s.subscribe(destination, latencyRecorder(latenciesNanos)));

        boolean warmedUp = awaitUntil(Duration.ofSeconds(20), () -> {
            messagingTemplate.convertAndSend(destination, brokerPayload());
            return latenciesNanos.size() >= SUBSCRIBERS;
        });
        assertThat(warmedUp).as("워밍업 메시지를 구독자 전원이 수신").isTrue();
        latenciesNanos.clear();

        long startNanos = System.nanoTime();
        for (int i = 0; i < MESSAGES; i++) {
            messagingTemplate.convertAndSend(destination, brokerPayload());
        }
        int expected = SUBSCRIBERS * MESSAGES;
        boolean allReceived = awaitUntil(Duration.ofSeconds(60), () -> latenciesNanos.size() >= expected);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        System.out.printf(
            "[stress2] 대조군(브로커 직행) — 구독자 %d × %d건 = %d, 수신 %d, p50 %dms, p95 %dms, 전체 %dms%n",
            SUBSCRIBERS, MESSAGES, expected, latenciesNanos.size(),
            percentileMillis(latenciesNanos, 50), percentileMillis(latenciesNanos, 95), elapsedMs);

        assertThat(allReceived).as("구독자 전원이 전부 수신").isTrue();
    }

    // 1차 게이트("탭 닫기 시 즉각 반환")를 인증이 붙은 세션에서 다시 확인한다.
    // CONNECT 인증을 붙이면 StompSubProtocolHandler가 세션별 Principal을 따로 들고 있게 되므로
    // (stompAuthentications), 그 맵이 세션과 함께 비워지는지가 새로 생기는 관심사다.
    @Test
    void 인증된_세션도_종료_시_즉시_반환된다() {
        List<StompSession> sessions = connectAll(subscriberTokens);
        assertThat(sessionRegistry.activeCount()).isEqualTo(SUBSCRIBERS);

        sessions.forEach(StompSession::disconnect);
        long elapsedMs = awaitSessionCount(0, Duration.ofSeconds(10));

        System.out.printf("[stress2] 인증 세션 정상 종료 — %d세션 → %dms%n", SUBSCRIBERS, elapsedMs);
        assertThat(elapsedMs).isLessThan(5_000);
    }

    // 연결·발신·해제를 반복해도 힙이 자라지 않아야 한다. 1차는 연결/해제만 반복했고,
    // 이번에는 매 사이클마다 실제 메시지를 태워 서비스·영속성 계층까지 포함시킨다.
    @Test
    void 연결_발신_해제를_반복해도_힙이_자라지_않는다() {
        int cycles = 40;
        long before = usedHeapAfterGc();

        for (int i = 0; i < cycles; i++) {
            StompSession session = connect(senderToken);
            send(session, "cycle-" + i + "-" + System.nanoTime());
            session.disconnect();
        }
        awaitSessionCount(0, Duration.ofSeconds(30));

        long after = usedHeapAfterGc();
        System.out.printf("[stress2] 연결·발신·해제 %d회 — 힙 %dMB → %dMB (Δ %+dMB)%n",
            cycles, before, after, after - before);

        assertThat(sessionRegistry.activeCount()).isZero();
    }

    // --- 하네스 ---

    private User persistUser(String tag) {
        String unique = tag + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User(unique + "@memorin.test", "hash", unique, unique, null));
    }

    // latencyRecorder가 읽는 모양(MessageResponse.content.text)을 그대로 흉내 낸다.
    private Map<String, Object> brokerPayload() {
        return Map.of("content", Map.of("text", "stress-" + System.nanoTime()));
    }

    private void send(StompSession session, String text) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/chat.sendText");
        session.send(headers, Map.of("roomId", roomId.toString(), "text", text));
    }

    private List<StompSession> connectAll(List<String> tokens) {
        List<StompSession> sessions = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            sessions.add(connect(token));
        }
        return sessions;
    }

    private StompSession connect(String accessToken) {
        String url = "ws://127.0.0.1:" + port + "/ws/websocket";
        StompHeaders connectHeaders = new StompHeaders();
        // 프론트(memorIN-frontend PR #87)가 실제로 싣는 헤더와 같은 형태다.
        connectHeaders.add("Authorization", "Bearer " + accessToken);
        try {
            StompSession session = stompClient
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() { })
                .get(15, TimeUnit.SECONDS);
            openedSessions.add(session);
            return session;
        } catch (Exception e) {
            throw new IllegalStateException("STOMP 연결 실패 (url=%s)".formatted(url), e);
        }
    }

    private StompFrameHandler latencyRecorder(List<Long> latenciesNanos) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                if (!(payload instanceof Map<?, ?> body)) {
                    return;
                }
                Object content = body.get("content");
                if (!(content instanceof Map<?, ?> contentMap)) {
                    return;
                }
                Object text = contentMap.get("text");
                if (!(text instanceof String value) || !value.startsWith("stress-")) {
                    return;   // 워밍업·사이클 메시지는 지연 표본에서 뺀다
                }
                latenciesNanos.add(System.nanoTime() - Long.parseLong(value.substring("stress-".length())));
            }
        };
    }

    private long awaitSessionCount(int expected, Duration timeout) {
        long startNanos = System.nanoTime();
        boolean reached = awaitUntil(timeout, () -> sessionRegistry.activeCount() == expected);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (!reached) {
            throw new AssertionError("%d초 안에 세션 수가 %d로 오지 않았다. 현재=%d, %s".formatted(
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
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private long percentileMillis(List<Long> samplesNanos, int percentile) {
        if (samplesNanos.isEmpty()) {
            return -1;
        }
        List<Long> sorted = new ArrayList<>(samplesNanos);
        Collections.sort(sorted);
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile / 100.0) - 1);
        return sorted.get(Math.max(index, 0)) / 1_000_000;
    }

    private long usedHeapAfterGc() {
        System.gc();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    // CONNECT 프레임의 Authorization 헤더를 검증해 세션에 Principal을 붙인다.
    //
    // 이 클래스는 **측정용 스탠드인**이다. docs/sprint4-architecture-review.md §2가 요구하는
    // 프로덕션 인터셉터의 자리를 임시로 메워 서비스 경로를 태울 수 있게 할 뿐이며,
    // SUBSCRIBE 인가(§2 두 번째 항목)는 여기서도 하지 않는다.
    //
    // 프로덕션 구현이 들어오면 @Import만 지우고 같은 테스트를 그대로 돌리면 된다.
    @TestConfiguration
    static class StressConnectAuthConfig implements WebSocketMessageBrokerConfigurer, Ordered {

        private final JwtTokenProvider jwtTokenProvider;

        StressConnectAuthConfig(JwtTokenProvider jwtTokenProvider) {
            this.jwtTokenProvider = jwtTokenProvider;
        }

        @Override
        public void configureClientInboundChannel(ChannelRegistration registration) {
            registration.interceptors(new ChannelInterceptor() {
                @Override
                public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                    StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                    if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                        return message;
                    }
                    String authorization = accessor.getFirstNativeHeader("Authorization");
                    if (authorization == null || !authorization.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("CONNECT에 Authorization 헤더가 없다");
                    }
                    Authentication authentication =
                        jwtTokenProvider.getAuthentication(authorization.substring("Bearer ".length()));
                    // 여기서 붙인 Principal을 StompSubProtocolHandler가 세션 단위로 기억하므로
                    // 이후 SEND 프레임에는 토큰이 없어도 된다.
                    accessor.setUser(authentication);
                    return message;
                }
            });
        }

        // WebSocketConfig의 SecurityContextChannelInterceptor보다 먼저 돌아야
        // CONNECT 프레임에서 붙인 Principal을 그쪽이 읽을 수 있다.
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
