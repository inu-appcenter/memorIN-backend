# Sprint 4 아키텍처 리뷰 — 채팅(WebSocket/STOMP) · 알림

> 작성: 2026-08-20 (Sprint 3 W8) · 작성자: 도영(BE 시니어)
> 대상: Sprint 4(Week 9~10) 채팅 · 알림 도메인 착수 전 결정 사항
> 근거: `origin/develop` @ `bbf7239` 코드 + PR [#169](https://github.com/inu-appcenter/memorIN-backend/pull/169)(미머지)

Sprint 4는 지금까지와 성격이 다르다. 게시물·댓글·팔로우는 **요청 하나가 끝나면 서버에 아무것도 남지 않는**
REST API였다. 채팅은 **연결이 살아 있는 동안 서버가 상태를 들고 있는다.** 그래서 지금까지 없던 실패 방식이 생긴다
— 세션이 안 끊기고 쌓이거나, 힙이 차서 프로세스가 죽는다.

이 문서는 코드를 쓰기 전에 정해야 하는 것만 모았다.

---

## 1. 현재 코드 상태 (착수 지점)

| 항목 | 상태 |
|---|---|
| 채팅 엔티티 (`ChatRooms` · `ChatRoomMembers` · `Messages` · `ChatEmoji`) | 있음 (V1 마이그레이션에 테이블도 있음) |
| `WebSocketConfig` · STOMP 브로커 | **develop에 없음.** PR #169가 들고 옴 |
| 채팅 REST API (방 생성/목록/메시지 조회) | 없음 |
| WebSocket 인증 | **없음.** `SecurityConfig`에 `/ws/**` permitAll만 있고 CONNECT 검증 없음 |
| 알림 저장 | `NotificationService.save()`는 있으나 **호출부 0개** |
| FCM 토큰 | 다중 토큰 저장/갱신 구현됨 (`POST /api/fcm/token`) |
| JVM 힙·컨테이너 메모리 제한 | **docker-compose.yml에 없음** |

---

## 2. 결정 1 — WebSocket 인증은 CONNECT 프레임에서 한다

**HTTP 필터(`JwtAuthenticationFilter`)로는 STOMP 메시지를 인증할 수 없다.** 필터는 핸드셰이크 요청 1회만 지나가고,
그 뒤 같은 TCP 연결로 흐르는 STOMP 프레임은 서블릿 필터 체인을 타지 않는다.
`/ws/**`가 permitAll인 지금 상태에서는 **누구나 연결해 아무 방이나 구독할 수 있다.**

### 채택

`ChannelInterceptor`를 `configureClientInboundChannel`에 등록하고, **CONNECT 프레임의 헤더에서 토큰을 검증**한다.

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String token = accessor.getFirstNativeHeader("Authorization"); // "Bearer xxx"
                // 검증 실패 시 예외 → 연결 자체가 수립되지 않는다
                accessor.setUser(authenticate(token));  // Principal 바인딩
            }
            return message;
        }
    });
}
```

- CONNECT에서 한 번 인증하면 **그 세션의 이후 모든 프레임에 Principal이 따라붙는다.**
- SUBSCRIBE 시점에는 "이 사용자가 이 방의 멤버인가"를 따로 검사해야 한다(인증 ≠ 인가).
  `/topic/rooms/{roomId}` 구독을 `ChatRoomMemberRepository.existsByRoomIdAndUserId`로 막지 않으면
  **아무나 남의 방을 엿볼 수 있다.**

### 주의 — `@AuthenticationPrincipal`은 그냥 되지 않는다

PR #169의 `MessageController`가 `@MessageMapping` 메서드에서 `@AuthenticationPrincipal UserDetailsImpl`을 받는데,
위 인터셉터가 없으면 **Principal이 없어 항상 `null`** 이다. 이 문제는 §7에 따로 적었다.

---

## 3. 결정 2 — InMemory 브로커의 세션 누수와 OOM

`enableSimpleBroker("/topic")`은 **구독 정보와 미전송 메시지를 전부 JVM 힙에 들고 있는다.**
Redis/RabbitMQ 같은 외부 브로커가 없으므로, 새는 곳은 전부 우리 힙이다.

터지는 경로는 셋이다.

| # | 경로 | 증상 |
|---|---|---|
| 1 | 클라이언트가 DISCONNECT 없이 사라짐(탭 강제 종료·네트워크 단절) | 서버가 죽은 세션을 계속 살아 있다고 믿는다 → 구독 레지스트리가 단조 증가 |
| 2 | 수신이 느린 클라이언트 | 보내지 못한 메시지가 세션 버퍼에 쌓인다 |
| 3 | 큰 메시지 | 프레임 하나가 힙을 크게 먹는다 |

### 채택 — 네 가지를 함께 건다

```java
// (1) 하트비트: 죽은 연결을 서버가 스스로 알아채는 유일한 수단
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[]{10_000, 10_000})   // 서버→클라 / 클라→서버 (ms)
            .setTaskScheduler(heartbeatScheduler());          // 스케줄러 없이 하트비트만 켜면 기동 시 실패한다
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
}

// (2)(3) 전송 제한: 느린 수신자와 큰 프레임이 힙을 먹는 것을 막는다
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    registry.setSendTimeLimit(10 * 1000)          // 10초 안에 못 보내면 세션을 끊는다
            .setSendBufferSizeLimit(512 * 1024)   // 세션당 미전송 버퍼 상한
            .setMessageSizeLimit(64 * 1024)       // 프레임 크기 상한
            .setTimeToFirstMessage(30 * 1000);    // 연결만 하고 CONNECT를 안 보내는 세션 정리
}
```

**(4) 세션 종료 시 정리 확인** — `SessionDisconnectEvent`를 구독해 방-세션 매핑을 실제로 지운다.
"탭을 닫으면 InMemory 세션이 즉각 반환된다"는 **Sprint 4 게이트 항목**이므로, 이벤트 리스너에서
현재 세션 수를 로그/메트릭으로 남겨 게이트 데모 때 눈으로 확인할 수 있게 한다.

> 숫자(10초·512KB·64KB·30초)는 출발점이지 정답이 아니다. W9 스트레스 테스트(§6)에서 실측해 조정한다.

---

## 4. 결정 3 — JVM 힙과 컨테이너 메모리 상한

현재 `docker-compose.yml`의 백엔드 서비스에는 **힙 설정도, 컨테이너 메모리 제한도 없다.**
지금까지는 요청이 끝나면 메모리가 회수돼 드러나지 않았지만, 세션이 쌓이는 채팅에서는 바로 문제가 된다.

- 제한이 없으면 JVM은 **호스트 메모리 기준**으로 힙을 잡는다. 개발자 노트북(16GB)과 서버(2GB)에서
  완전히 다르게 동작한다 → "제 컴퓨터에선 되는데요"가 여기서 나온다.
- 컨테이너 제한만 걸고 힙을 안 잡으면 JVM이 한도를 모른 채 커지다 **OOMKilled(137)** 로 조용히 죽는다.
  애플리케이션 로그에는 아무것도 남지 않는다.

### 채택

```yaml
backend:
  environment:
    # 컨테이너에 준 메모리의 75%를 힙 상한으로. 고정값(-Xmx)보다 이식성이 좋다.
    JAVA_TOOL_OPTIONS: >-
      -XX:MaxRAMPercentage=75.0
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/tmp/heapdump.hprof
  deploy:
    resources:
      limits:
        memory: 1g
```

`HeapDumpOnOutOfMemoryError`는 특히 중요하다. 세션 누수는 **터진 뒤에 힙을 봐야** 원인을 안다.

---

## 5. 결정 4 — 메시지 저장은 커밋 후에 브로드캐스트한다

로드맵의 "STOMP 메시지 처리 + 비동기 DB 저장"을 그대로 구현할 때 흔한 실수가 있다.
**저장과 브로드캐스트의 순서**다.

| 순서 | 문제 |
|---|---|
| 브로드캐스트 → 저장 | 저장이 실패하면 화면에는 있는데 DB에 없는 메시지가 생긴다. 새로고침하면 사라진다 |
| 저장(트랜잭션 안) → 같은 트랜잭션에서 브로드캐스트 | 롤백돼도 메시지는 이미 나갔다. 같은 문제 |
| **커밋 후 브로드캐스트** | 채택 |

```java
// 트랜잭션이 커밋된 뒤에만 내보낸다
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override public void afterCommit() {
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, response);
        }
    });
```

또는 `@TransactionalEventListener(phase = AFTER_COMMIT)`를 쓴다.

"비동기 저장"으로 응답 지연을 줄이고 싶다면 **저장 자체를 큐에 넘기는 것**이지, 커밋 전에 먼저 쏘는 것이 아니다.
비동기로 갈 경우 실패한 저장을 어떻게 되돌릴지(재시도·DLQ)를 함께 정해야 하므로,
**Sprint 4에서는 동기 저장 + 커밋 후 브로드캐스트로 시작하고**, 스트레스 테스트에서 지연이 문제가 되면 그때 비동기로 옮긴다.

---

## 6. 스트레스 테스트 설계 (W9 목 · 내 담당)

게이트 문구는 "탭 닫기 시 InMemory 세션 즉각 반환"이다. 이걸 **숫자로** 확인한다.

| 측정 | 방법 | 합격 기준(초안) |
|---|---|---|
| 세션 정상 반환 | N개 연결 → 정상 DISCONNECT → 세션 수 | 0으로 복귀 |
| **비정상 종료 회수** | N개 연결 → 소켓 강제 종료(FIN 없이) → 하트비트 주기 경과 후 세션 수 | RST는 즉시(≈0.1초), 무응답은 **하트비트 × 3 + 태스크 주기** (10초 설정 → ≈35초). 초안의 "2~3주기"는 Spring의 `HEARTBEAT_MULTIPLIER=3`을 빠뜨린 값이었다 |
| 힙 증가 | 연결·해제 100회 반복 후 Full GC → 힙 사용량 | 초기값 대비 증가 없음(누수 없음) |
| 브로드캐스트 지연 | 그룹 방 M명, 초당 K건 → 수신 지연 p95 | 실측 후 기준 확정 |
| 느린 수신자 격리 | 한 클라이언트만 수신 지연 → 다른 클라이언트 지연 | 영향 없음(§3의 버퍼 제한 검증) |

**1차 실행 결과: `docs/ws-stress-test.md`** (2026-09-01 · 6개 시나리오 전부 통과, 무응답 회수 기준만 정정)

도구는 JMeter/Gatling보다 **STOMP 클라이언트를 직접 붙인 JUnit 시나리오**가 낫다 — 세션 수·힙을
같은 JVM에서 바로 읽을 수 있다. 측정 원칙은 `docs/n+1-audit.md` §3과 같다: **추측하지 말고 실측한다.**

---

## 7. PR #169 — Sprint 4 범위가 Sprint 3에 먼저 들어왔다

게시물 공유 API PR이 `WebSocketConfig` · `MessageController` · `MessageService`를 함께 들고 온다.
게시물 공유는 Sprint 3 태스크지만, **그 전달 경로가 채팅이라 Sprint 4 기반이 먼저 필요해진 것**이다.

이 PR을 그대로 머지하면 안 되는 이유는 코드 리뷰에 남겼고, 아키텍처 관점의 요지는 다음과 같다.

| 항목 | 현재 PR | 이 문서의 결정 |
|---|---|---|
| WebSocket 인증 | 없음. `@AuthenticationPrincipal`이 `null`이 된다 | §2 CONNECT 인터셉터 |
| 발신자 식별 | `UUID.fromString(userDetails.getUsername())` — `getUsername()`은 **닉네임**이라 UUID 파싱이 실패한다. 다른 컨트롤러는 전부 `getUserId()`를 쓴다 | `getUserId()` |
| Origin 정책 | `setAllowedOriginPatterns("*")` | `CORS_ALLOWED_ORIGINS` 환경변수 재사용(§8) |
| 브로커 튜닝 | 기본값 | §3 하트비트·버퍼·크기 제한 |
| 저장/브로드캐스트 순서 | 트랜잭션 안에서 저장 후 즉시 전송 | §5 커밋 후 전송 |
| 구독 인가 | 없음 | §2 SUBSCRIBE 시 멤버 검사 |

**처리 방침(플래닝 안건):** 공유 API의 **REST 부분과 STOMP 부분을 분리**하고,
STOMP 기반(`WebSocketConfig`·인증 인터셉터)은 Sprint 4 첫 태스크로 옮긴다.
그래야 Sprint 3 게이트("게시물 공유 API 통합 테스트")를 채팅 인프라 완성 없이도 닫을 수 있다.

---

## 8. 결정 5 — WebSocket Origin은 CORS 설정을 재사용한다

`setAllowedOriginPatterns("*")`는 Spring Security의 CORS 설정과 **별개로 동작한다.**
Sprint 1에서 dev/prod 오리진을 나눠 놓았는데 WebSocket만 전부 열어두면 그 작업이 무의미해진다.

`CorsConfig`가 이미 `@Value("${cors.allowed-origins}")`로 오리진 목록을 주입받고 있다.
WebSocket도 같은 값을 쓴다.

```java
public WebSocketConfig(@Value("${cors.allowed-origins}") List<String> allowedOrigins) { ... }

registry.addEndpoint("/ws")
        .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
        .withSockJS();
```

`cors.allowed-origins`는 dev(`application.properties`)와 prod(`application-docker.properties` ←
`CORS_ALLOWED_ORIGINS` 환경변수)로 갈라져 있고, `docker-compose.yml`이 컨테이너에 전달한다(#156에서 누락을 고쳤다).

---

## 9. 결정 6 — 알림 파이프라인의 빈 칸

Sprint 4의 FCM/Web Push는 **"저장된 알림을 발송한다"** 를 전제로 하는데, 지금은 그 앞 단계가 비어 있다.

```
[발생 지점]  →  [저장]  →  [발송]
 팔로우 요청      NotificationService.save()      FCM / Web Push
 팔로우 수락      ← 호출하는 곳이 하나도 없다      ← Sprint 4
 댓글 작성                (구멍)
```

발생 지점을 어디에 붙일지 두 가지 안이 있다.

| 안 | 장점 | 단점 |
|---|---|---|
| 서비스에서 직접 호출 (`FollowService` → `NotificationService`) | 단순·명시적 | 도메인 간 결합. 알림 실패가 본 기능 트랜잭션에 영향 |
| **스프링 이벤트 발행 → 알림 리스너가 `AFTER_COMMIT`에 처리** | 결합 분리, 본 기능 트랜잭션과 격리 | 흐름이 한 단계 간접적 |

§5와 같은 이유로 **이벤트 + AFTER_COMMIT을 권한다.** 팔로우가 롤백됐는데 알림만 나가는 상황을 막아야 한다.
어느 쪽이든 **Sprint 4 착수 전에 정해야** FCM 태스크가 붕 뜨지 않는다.

---

## 10. Sprint 4 착수 전 체크리스트

- [ ] WebSocket CONNECT 인증 인터셉터 (§2) — 담당 지정 필요
- [ ] SUBSCRIBE 시 방 멤버 인가 (§2)
- [x] 브로커 하트비트·버퍼·메시지 크기 제한 (§3) — 적용 완료. 세션 카운터(`WebSocketSessionRegistry`)까지. 방-세션 매핑 삭제는 채팅방 API(#188) 이후
- [x] `docker-compose.yml`에 `MaxRAMPercentage` + 메모리 limit + 힙덤프 (§4) — 적용 완료. 힙덤프 경로는 `/tmp` 대신 `/dump` 볼륨(컨테이너와 함께 사라지지 않도록)
- [ ] 저장→커밋→브로드캐스트 순서 합의 (§5)
- [ ] PR #169의 STOMP 부분 분리 여부 결정 (§7)
- [x] WebSocket Origin을 `CORS_ALLOWED_ORIGINS`로 통일 (§8) — 적용 완료 (`"*"` 제거)
- [ ] 알림 발생 지점 연결 방식 결정 (§9)
- [ ] 채팅 화면 디자인 FE 전달 (Sprint 3 게이트 · 회의 확인)

## 11. 관련 문서

- `docs/api-spec-domains.md` §10: 채팅 REST/STOMP 명세 초안 · §11: 알림 API
- `docs/auth-jwt-design.md` §6: WebSocket 토큰 인증 초안
- `docs/n+1-audit.md` §3: 실측 원칙 (스트레스 테스트도 같은 원칙)
