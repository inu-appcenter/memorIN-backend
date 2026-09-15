# Sprint 4 코드 리뷰 — 채팅·알림 도메인 + PR #187

> 작성: 2026-09-05 · 대상: `origin/develop` @ `933f579` + 미머지 PR [#187](https://github.com/inu-appcenter/memorIN-backend/pull/187)
> 범위: 백엔드. Week 10 화요일(PR #187 리뷰) · 수요일(Sprint 4 전체 코드 리뷰) 태스크의 산출물이다.
> 짝 문서: `docs/sprint4-architecture-review.md`(착수 전 결정) · `docs/ws-stress-test.md`(생명주기 실측)

Sprint 4 완료 기준을 **코드 실행 경로로** 대조한 기록이다. 문서의 체크박스가 아니라
"지금 배포하면 실제로 되는가"를 기준으로 판정했다.

기동을 막거나 게이트를 막는 것부터 적는다.

---

## 0. 한눈에

| 심각도 | 건수 | 내용 |
|---|---|---|
| 🔴 차단 | 4 | Flyway V9 중복 · 인증 주체 타입 불일치 2곳 · CONNECT 인증 부재 |
| 🟠 결함 | 7 | 구독 인가 없음 · 나간 멤버 발신 허용 · 응답 봉투 미적용 · 500 누출 · 검색 가시성 규칙 이탈 · 커서 페이징 미적용 · 미디어 null 혼입 |
| 🟡 개선 | 8 | 데드코드 · N+1 · 미사용 import · 오타 · 상한 없는 입력 등 |

---

## 1. 🔴 차단 — `@AuthenticationPrincipal`에 `UUID`를 받고 있다 (2곳)

`JwtTokenProvider.getAuthentication()`이 principal에 넣는 것은 **`UserDetailsImpl`**이다.
`@AuthenticationPrincipal`의 리졸버는 타입이 안 맞으면 예외를 던지지 않고 **조용히 `null`을 주입한다**
(`errorOnInvalidType` 기본값 `false`).

### 1-1. `ChatRoomController` — 7개 엔드포인트 전부

```java
public ChatRoomResponse createDirectRoom(@RequestBody CreateDirectRoomRequest request,
                                         @AuthenticationPrincipal UUID requesterId) {   // ← 항상 null
    return chatRoomService.createDirectRoom(requesterId, request.targetUserId());
}
```

`ChatRoomService.createDirectRoom`의 첫 줄이 `requesterId.equals(targetUserId)`이므로 **NPE → 500**이다.
나머지 6개도 `userRepository.findById(null)` 또는 `requireActiveMember(room, null)`로 이어진다.
`ChatRoomController.java:38,49,58,68,81,90,100` 전부 해당한다.

**왜 테스트가 못 잡았나** — `ChatRoomTest`가 `ChatRoomService`를 직접 주입받아 호출한다.
서비스 계층은 정상이고 컨트롤러만 틀렸다. 채팅방 API는 **아직 한 번도 HTTP로 호출된 적이 없다**는 뜻이다.

**수정**: 다른 컨트롤러와 동일하게 `@AuthenticationPrincipal UserDetailsImpl userDetails` +
`userDetails.getUserId()`. 같은 결함이 Sprint 3 PR #169 리뷰(§3)에서도 지적됐던 항목이다.

### 1-2. `PostController.search` (PR #187) — 조용히 결과가 틀린다

```java
@AuthenticationPrincipal UUID viewerId
```

이쪽은 500이 아니라 **결과가 틀린다.** `viewerId`가 `null`로 내려가면
`WHERE (p.visibility = 'PUBLIC' OR p.user_id = :viewerId)`의 뒤쪽 조건이 항상 거짓이 되어,
**로그인한 사용자가 자기 비공개 글을 검색으로 못 찾는다.** 500이 안 나므로 더 늦게 발견된다.

`PostSearchTest`도 `PostService`를 직접 주입받으므로 이 경로를 타지 않는다.

> **회귀 방지 제안**: 컨트롤러를 MockMvc로 태우는 테스트가 두 도메인 모두 없다.
> 최소한 "인증된 사용자가 호출하면 200이 나온다" 수준의 슬라이스 테스트 1개씩만 있어도
> 이 두 건은 즉시 잡혔다. Sprint 5 이월 후보.

---

## 2. 🔴 차단 — PR #187의 Flyway 버전이 V9로 중복된다

| 위치 | 파일 |
|---|---|
| develop (#192 머지됨) | `V9__add_web_push_subscriptions.sql` |
| PR #187 | `V9__add_column_tag_at_post_.sql` |

파일명이 달라 GitHub은 `MERGEABLE`로 표시하지만, 같은 버전이 두 개면 Flyway가
`Found more than one migration with version 9`로 **기동 자체를 실패시킨다.**

Sprint 3 PR #169에서 똑같은 사고가 있었고(그때는 V7 중복), 그 리뷰 이후 재발이다.

**수정**: PR #187의 두 마이그레이션을 `V10`, `V11`로 리넘버(현재 PR의 V10도 함께 밀린다).
브랜치를 develop에 리베이스한 뒤 `db/migration` 디렉터리의 최대 버전을 확인하고 번호를 잡는다.

> 근본 대책으로 "머지 전 버전 중복 검사"를 CI에 한 줄 넣는 것을 제안한다.
> 같은 사고가 두 스프린트 연속으로 났다.

---

## 3. 🔴 차단 — STOMP CONNECT 인증이 없는데 FE는 이미 토큰을 보내고 있다

`docs/sprint4-architecture-review.md` §2의 "CONNECT 인터셉터"가 **담당 미지정 상태로 미구현**이다.
현재 `WebSocketConfig`에는 `SecurityContextChannelInterceptor`만 있고, 이건 핸드셰이크 시점에
이미 인증이 끝나 있을 때 그 `Authentication`을 메시지 스레드로 옮겨주는 역할이다.
`/ws/**`는 `permitAll`이고 브라우저 WebSocket은 헤더를 못 실으므로 **인증이 붙을 자리가 없다.**

문제는 프론트가 이미 자기 몫을 끝냈다는 점이다. `memorIN-frontend` PR #87:

```ts
beforeConnect: async () => {
  const token = await resolveConnectToken();
  instance.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
},
```

주석에는 "서버가 CONNECT를 검증해 이 세션 = 유저 A로 기록한다"라고 적혀 있다.
**양쪽 다 끝난 것처럼 보이지만 붙이면 `@AuthenticationPrincipal`이 null이라 첫 메시지에서 깨진다.**

완료 기준 "1:1 채팅 실시간 송수신"과 "그룹 채팅 다중 수신"이 여기서 막힌다.
Week 10 플래닝에서 **담당자부터 정해야 한다.**

---

## 4. 🟠 `/topic/rooms/{roomId}` 구독 인가가 없다

```java
messagingTemplate.convertAndSend("/topic/rooms/" + request.roomId(), response);
```

`enableSimpleBroker("/topic")`은 구독 시점에 아무 검사도 하지 않는다.
CONNECT 인증이 붙더라도 **로그인만 하면 남의 방 roomId를 구독해 대화를 전부 받아볼 수 있다.**
roomId는 UUIDv7이라 추측이 어렵지만, 공유 링크·로그·FE 상태 등으로 새면 그대로 열린다.

아키텍처 리뷰 §2의 "SUBSCRIBE 시 방 멤버 검사"가 미구현 상태다. §3의 CONNECT 인터셉터와
같은 인터셉터에서 `StompCommand.SUBSCRIBE`를 함께 처리하면 된다.

---

## 5. 🟠 나간·강퇴당한 멤버가 계속 메시지를 보낼 수 있다

`MessageService`의 참여자 검증(`MessageService.java:53`, `:81`):

```java
if (!chatRoomMemberRepository.existsByRoomIdAndUserId(request.roomId(), senderId)) { ... }
```

`existsByRoomIdAndUserId`는 **`left_at`을 보지 않는다.** `chat_room_members`는 `uq_room_member`
제약 때문에 나갈 때 행을 지우지 않고 `left_at`만 채운다(`ChatRoomMembers.leave()`).
따라서 **나간 사람도, 강퇴당한 사람도 행이 남아 있어 검증을 통과한다.**

`ChatRoomService`는 같은 상황을 `requireActiveMember()`(= `filter(ChatRoomMembers::isActive)`)로
올바르게 처리하고 있다. 메시지 경로만 빠졌다.

**수정**: `existsByRoomIdAndUserIdAndLeftAtIsNull(...)`로 바꾸고, 두 서비스가 같은 헬퍼를 쓰게 한다.
`ChatRoomController`의 TODO("강퇴당한 대상이 재입장 가능")와도 같은 뿌리다.

---

## 6. 🟠 `/api/chat-rooms`만 전역 응답 봉투를 쓰지 않는다

`ChatRoomController`는 `ChatRoomResponse` / `List<ChatRoomSummaryResponse>` / `void`를 그대로 반환한다.
다른 컨트롤러는 전부 `ApiResponse<T>`로 감싼다(`FollowController`, `WebPushSubscriptionController`, …).

Sprint 3의 #165가 정확히 이 결함("공개 프로필 API가 전역 응답 포맷을 안 지킨다")이었고,
FE는 `ApiResponse` 언래핑을 전제로 클라이언트를 짜고 있다. 지금 고치지 않으면 FE 연동 시점에
채팅방 API만 예외 처리를 하나 더 만들게 된다.

---

## 7. 🟠 정상적인 정책 위반이 500으로 나간다

```java
throw new IllegalArgumentException("자기 자신과 1:1 채팅방을 만들 수 없습니다.");  // ChatRoomService.java:38
throw new IllegalStateException("1:1 채팅방에는 사용할 수 없는 기능입니다.");        // ChatRoomService.java:160
```

`GlobalExceptionHandler`에 이 두 예외의 핸들러가 없으므로 `handleUnexpectedException(Exception)`으로
떨어져 **500 + `COMMON_001`**이 나간다. 사용자 입력 문제인데 서버 장애로 보고된다.

이슈 #89("존재하지 않는 경로나 잘못된 메서드로 호출해도 500이 반환됨")에서 정리한 원칙의 재발이다.
`ErrorCode.CHAT_ROOMS_002`(400)가 이미 있으므로 `BusinessException`으로 바꾸면 끝난다.

---

## 8. 🟠 PR #187 — 검색만 가시성 규칙이 다르다

```java
sql.append(" AND (p.visibility = 'PUBLIC' OR p.user_id = :viewerId)");
```

`PostAccessPolicy`는 `FRIENDS`를 **양방향 ACCEPTED 팔로우면 열람 허용**으로 판정한다.
검색 쿼리는 `FRIENDS`를 아예 제외하므로 **친구가 친구공개로 올린 글이 검색에서 통째로 사라진다.**

이슈 #141이 "친구 판정 로직 중복 통합 → `PostAccessPolicy` 단일화"였고, 그 결정이 검색에는
적용되지 않았다. 서비스 주석도 "공개 글이거나 본인 글로 이미 필터링"이라고 이 전제를 굳히고 있다.

**수정 방향**: 친구 목록 서브쿼리를 WHERE에 넣거나(친구 피드 `findFriendFeed`와 같은 방식),
최소한 이번 스프린트에서는 "검색은 PUBLIC만 대상"이라고 **명세와 이슈에 명시**하고 넘어간다.
지금은 코드와 이슈(#181)의 서술이 서로 다르다.

---

## 9. 🟠 PR #187 — 커서 페이징이 아니라 OFFSET 페이징이다

```java
" ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset"
...
long total = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM posts p WHERE " + where.sql()) ...
```

이슈 #181의 완료 조건 1번은 "**커서 페이지네이션**으로 반환된다"이고, 이 레포는 피드·알림·팔로우·유저 검색을
전부 커서로 통일해 왔다(`PostCursor`가 이미 있고 `PostService`가 쓰고 있다).
검색만 `Page<T>` + `COUNT(*)`로 돌아갔다.

- 이슈 완료 조건 미충족이다. **이슈 #181의 체크박스는 전부 체크돼 있지만 실제로는 1·2번이 안 맞는다.**
- 깊은 페이지에서 OFFSET 스캔 비용이 그대로 든다. 매 요청 COUNT도 함께 나간다.
- FE 무한 스크롤 컴포넌트가 커서 응답(`items`/`nextCursor`/`hasNext`)에 맞춰져 있어 응답 형태도 다르다.

---

## 10. 🟠 PR #187 — 미디어 URL 생성 실패 시 `null`이 응답 배열에 섞인다

```java
private PostMediaResponse toMediaResponse(PostMedia media) {
    try { ... } catch (Exception e) { return null; }   // ← 리스트 원소가 null이 된다
}
```

`attachments`가 `[null, {...}]` 형태로 나갈 수 있다. 기존 경로는 `toMediaResponses()` +
`resolveDownloadUrl()`을 쓰는데 이번 PR이 같은 일을 하는 두 번째 메서드를 새로 만들면서 처리가 갈렸다.
**기존 헬퍼를 재사용하면 된다.**

---

## 11. 🟡 나머지 — 지금 고치면 싼 것들

| # | 위치 | 내용 |
|---|---|---|
| 1 | `MessageService.java:23,28,30-33` | `lombok.Data`, `java.util.Date`, `BlockingQueue`, `Executors`, `LinkedBlockingQueue`, `ScheduledExecutorService` **미사용 import**. 비동기 큐를 만들다 만 흔적으로 보인다 — W9 목요일 "비동기 DB 저장" 태스크의 잔재라면 **그 태스크는 미적용 상태**로 봐야 한다 |
| 2 | `ChatRoomMembers.of(room, post, member)` | 프로덕션 호출부 0개. 테스트 2곳(`PostShareTest`, `TextMessageTest`)만 쓴다. `ofOwner`/`ofMember`로 정리 |
| 3 | `ChatRoomService.java:150` | `listMyRooms`가 `m.getRoom()` LAZY 접근 → **방 개수만큼 추가 쿼리**(N+1). 채팅 첫 화면이라 체감된다. `@EntityGraph` 또는 fetch join |
| 4 | `ChatRoomsRepository` | `CrudRepository`라서 `MessageService`에 `(ChatRooms)` 캐스팅이 남아 있다. `JpaRepository<ChatRooms, UUID>`로 바꾸면 캐스팅·`findById` 재선언 둘 다 없어진다 |
| 5 | `CreateGroupRoomRequest` / `InviteMembersRequest` | `memberIds`에 **상한도 중복 검사도 없다**. 중복 UUID가 들어오면 `uq_room_member` 위반으로 500, 큰 배열은 그만큼 `findById`가 반복된다 |
| 6 | `NotificationType` | 채팅 메시지용 타입이 없다(`FOLLOW_REQUEST`/`FOLLOW_ACCEPTED`/`COMMENT`/`LIKE`). **메시지 수신 푸시를 만들려면 타입부터 추가**해야 한다. `LIKE`는 #148에서 폐기됐는데 남아 있다(#182 결정 대기) |
| 7 | `TagType.ECT` (PR #187) | `ETC` 오타로 보인다. DB 값·FE 계약에 박히기 전인 지금이 고칠 시점 |
| 8 | `V10__add_content_trgm_index.sql` (PR #187) | `CREATE EXTENSION pg_trgm`은 슈퍼유저 권한이 필요하다. 운영 DB 롤을 분리해 두었다면(`docs/pgadmin-onboarding-grant-guide.md`) 마이그레이션이 권한 오류로 멈춘다 — 배포 전 확인 필요 |

`PostSearchRequest`의 주석("enum 값들의 AND 매칭")도 실제 동작과 다르다 —
정확도순 정렬일 때는 OR(하나라도 겹치면 후보)로 동작한다. 미사용 import(`java.sql.Date`, `LocalDate`)도 있다.

---

## 12. 완료 기준 대비 — 코드로 본 현재 위치

| 완료 기준 | 판정 | 막는 것 |
|---|---|---|
| 1:1 실시간 송수신 (STOMP) | ❌ | §3 CONNECT 인증 |
| 그룹 채팅방 생성 + 다중 수신 | ❌ | §1-1 컨트롤러 500, §3 |
| FCM 백그라운드 푸시 | 🟡 | 코드 완성·기본 비활성. 채팅 메시지 트리거 없음(§11-6), 통합 테스트 미실행 |
| Web Push | 🟡 | BE 완성. FE Service Worker 미착수 |
| 탭 닫기 시 세션 반환 | 🟢 | 실측 101ms — PR #194 머지 대기 |
| 채팅 내역 무한 스크롤 | ❌ | BE 미착수(#195) |
| 게시물 공유 카드 렌더링 | 🟡 | BE·FE 있으나 전송 경로가 §3에 막힘 |
| 데스크탑 Master-Detail | ✅ | FE #78 머지 |

**정리하면 게이트 8개 중 실제로 서 있는 것은 1개(Master-Detail)이고, 1개(세션 반환)가 머지 대기다.**
나머지 6개는 §1·§3 두 건을 풀지 않으면 데모 자체가 불가능하다.

---

## 13. 권고 순서

1. **§1-1 `ChatRoomController` principal 타입** — 한 줄짜리 수정인데 채팅 API 전체를 막고 있다 (건희님)
2. **§3 CONNECT 인증 인터셉터** — 담당자 배정이 먼저다. FE는 이미 대기 중이다 (플래닝 안건)
3. **§2 PR #187 V9 리넘버** — 머지 전 필수 (건희님)
4. §4 SUBSCRIBE 인가 · §5 나간 멤버 발신 — 인증 인터셉터와 같은 작업 단위로 묶으면 싸다
5. §6·§7 응답 봉투·예외 포맷 — FE 연동 전에 (건희님)
6. §8·§9·§10 PR #187 나머지 — 리뷰 반영 후 재요청
