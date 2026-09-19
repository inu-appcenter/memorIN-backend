# 컨트롤러 경유 테스트 커버리지

> 작성: 2026-09-14 (Sprint 5 W11) · 대상: `origin/develop` + PR #248
> 이슈: #204

**엔드포인트 47개 중 인증이 필요한 것은 38개**다. 그중 컨트롤러를 실제로 지나가는 테스트가 있는
것이 얼마나 되는지, 없는 곳은 어디인지 기계적으로 대조한 기록이다.

---

## 1. 왜 이 문서가 필요한가

지금 테스트는 대부분 **서비스를 직접 호출**한다.

```java
chatRoomService.createDirectRoom(ids[0], ids[1]);   // UUID를 직접 넘김
postService.search(ids[1], scoped, null, 20);       // UUID를 직접 넘김
```

서비스 로직은 멀쩡하니 테스트는 통과하고 CI는 green이다. 그런데 컨트롤러를 지나가지 않으므로
`@AuthenticationPrincipal` 해석·요청 바인딩·응답 형식 같은 **컨트롤러 계약이 검증되지 않는다.**

**이 구멍으로 실제 장애가 릴리스까지 나갔다.** 채팅방 7개 엔드포인트와 검색 API가
`@AuthenticationPrincipal UUID`로 받아 `null`이 주입되는 상태로 `main`에 배포됐다(#198 · #200 → #207).

증상이 둘로 갈렸다는 점이 중요하다.

| 경로 | 증상 | 발견 난이도 |
|---|---|---|
| 채팅방 | 서비스 진입부에서 NPE → **500** | 금방 드러난다 |
| 검색 | `viewerId`가 null → SQL 조건이 UNKNOWN → **PUBLIC만 반환** | 에러가 없어 **조용히 결과만 틀리다** |

뒤쪽이 이 문서가 존재하는 이유다. **200이 떨어지는 것만 봐서는 잡을 수 없다.**

---

## 2. 두 겹으로 막는다

### 2-1. `AuthenticationPrincipalContractTest` — 전체를 한 번에

등록된 핸들러를 전부 훑어 **모든 `@AuthenticationPrincipal` 파라미터가 `UserDetailsImpl`인지**
검사한다. 현재 **38개**를 검사한다.

`AuthenticationPrincipalArgumentResolver`는 타입이 맞지 않으면 예외를 던지지 않고
**조용히 `null`을 주입한다**(`errorOnInvalidType` 기본값 `false`). 그래서 잘못 선언해도
컴파일도 기동도 되고 CI도 green이다.

이 테스트의 장점은 **새 엔드포인트가 자동으로 포함된다**는 것이다. 사람이 기억해서 테스트를
붙이지 않아도 타입 실수만은 막힌다.

> 같은 파일에서 클래스 레벨 `@RequestMapping`이 `/`로 시작하는지도 검사한다.
> 실제로 `UserController`만 `@RequestMapping("api/users")`로 슬래시가 빠져 있었다(이 PR에서 수정).

### 2-2. 엔드포인트별 슬라이스 테스트 — 값이 올바른 자리로 가는가

타입이 맞아도 **엉뚱한 값이 갈 수** 있다. 인자 순서가 뒤바뀌어도 둘 다 `UUID`면 컴파일된다.
그래서 `ArgumentCaptor`로 **서비스에 도달한 UUID를 직접 붙잡아** 확인한다.

- `ChatRoomSearchAuthTest` — 채팅방 생성·목록, 게시물 검색 (#207에서 신설)
- `AuthenticatedEndpointSliceTest` — 알림 3개, 팔로우 2개, 메시지 히스토리 (이 PR에서 신설)
- `PostErrorResponseTest` · `MediaControllerTest` — 오류 응답 계약 중심

---

## 3. 현황 (47개 전수)

| 도메인 | 메서드 | 경로 | 인증 | 컨트롤러 테스트 |
|---|---|---|:--:|---|
| Auth | `POST` | `/auth/signup` | - | — |
| Auth | `POST` | `/auth/login` | - | — |
| Auth | `POST` | `/auth/refresh` | - | — |
| Auth | `DELETE` | `/auth/logout` | O | ❌ |
| ChatRoom | `POST` | `/api/chat-rooms/direct` | O | ✅ ChatRoomSearchAuthTest |
| ChatRoom | `POST` | `/api/chat-rooms/group` | O | ❌ |
| ChatRoom | `GET` | `/api/chat-rooms` | O | ✅ ChatRoomSearchAuthTest |
| ChatRoom | `POST` | `/api/chat-rooms/{roomId}/members` | O | ❌ |
| ChatRoom | `DELETE` | `/api/chat-rooms/{roomId}/members/{targetUserId}` | O | ❌ |
| ChatRoom | `DELETE` | `/api/chat-rooms/{roomId}/members/me` | O | ❌ |
| ChatRoom | `PATCH` | `/api/chat-rooms/{roomId}/name` | O | ❌ |
| CommentEmoji | `POST` | `/api/comments/{commentId}/emojis` | O | ❌ |
| CommentEmoji | `DELETE` | `/api/comments/{commentId}/emojis/{emojiType}` | O | ❌ |
| CommentEmoji | `GET` | `/api/comments/{commentId}/emojis` | O | ❌ |
| FcmToken | `POST` | `/api/fcm/token` | O | ❌ |
| FcmToken | `DELETE` | `/api/fcm/token` | O | ✅ FcmTokenControllerTest |
| Follow | `POST` | `/api/follows` | O | ❌ |
| Follow | `PATCH` | `/api/follows/{followId}/accept` | O | ✅ AuthenticatedEndpointSliceTest |
| Follow | `DELETE` | `/api/follows/requests/{followId}` | O | ❌ |
| Follow | `DELETE` | `/api/follows/{followingId}` | O | ❌ |
| Follow | `GET` | `/api/follows/requests` | O | ✅ AuthenticatedEndpointSliceTest |
| Message | `GET` | `/api/chat-rooms/{roomId}/messages` | O | ✅ AuthenticatedEndpointSliceTest |
| Notification | `GET` | `/api/notifications` | O | ✅ AuthenticatedEndpointSliceTest |
| Notification | `PATCH` | `/api/notifications/{notificationId}/read` | O | ✅ AuthenticatedEndpointSliceTest |
| Notification | `PATCH` | `/api/notifications/read-all` | O | ✅ AuthenticatedEndpointSliceTest |
| PostComment | `POST` | `/api/posts/{postId}/comments` | O | ❌ |
| PostComment | `GET` | `/api/posts/{postId}/comments` | O | ❌ |
| PostComment | `PATCH` | `/api/comments/{commentId}` | O | ❌ |
| PostComment | `DELETE` | `/api/comments/{commentId}` | O | ❌ |
| Post | `POST` | `/api/posts` | O | ✅ PostErrorResponseTest |
| Post | `GET` | `/api/posts/{postId}` | O | ✅ PostErrorResponseTest |
| Post | `GET` | `/api/posts` | O | ✅ PostErrorResponseTest |
| Post | `PATCH` | `/api/posts/{postId}` | O | ❌ |
| Post | `DELETE` | `/api/posts/{postId}` | O | ❌ |
| Post | `GET` | `/api/posts/friends` | O | ❌ |
| Post | `GET` | `/api/posts/recommend` | - | — |
| Post | `GET` | `/api/posts/search` | O | ✅ ChatRoomSearchAuthTest |
| User | `GET` | `/api/users/me` | O | ❌ |
| User | `GET` | `/api/users/search` | - | — |
| User | `GET` | `/api/users/{userId}/followers` | - | — |
| User | `GET` | `/api/users/{userId}/followings` | - | — |
| User | `GET` | `/api/users/{userId}` | - | — |
| WebPushSubscription | `POST` | `/api/web-push/subscriptions` | O | ❌ |
| WebPushSubscription | `DELETE` | `/api/web-push/subscriptions` | O | ❌ |
| Media | `POST` | `/api/media/presigned-upload-url` | O | ✅ MediaControllerTest |
| Media | `GET` | `/api/media/compression-policy` | - | ✅ MediaControllerTest |
| Media | `GET` | `/api/media/quota` | O | ✅ MediaControllerTest |
| Media | `GET` | `/api/media/{postMediaId}/presigned-download-url` | O | ✅ MediaControllerTest |

---

## 4. 남은 공백 23개 — 우선순위

"인증이 필요한데 컨트롤러 경유 테스트가 없는" 엔드포인트다.
**전부 채우는 것이 목표가 아니다.** 틀렸을 때 조용한 것부터 채운다.

### 🔴 1순위 — 틀리면 남의 데이터가 나온다 (에러 없음)

| 엔드포인트 | 위험 |
|---|---|
| `GET /api/users/me` | principal이 틀리면 **남의 마이페이지**가 내려간다 |
| `GET /api/posts/friends` | `viewerId`가 틀리면 남의 친구 피드를 본다 |
| `GET /api/comments/{commentId}/emojis` | 내 반응 여부 표시가 어긋난다 |
| `POST /api/web-push/subscriptions` | 구독이 **남의 계정에 붙는다** — 알림이 엉뚱한 사람에게 간다 |
| `DELETE /api/web-push/subscriptions` | 남의 구독을 해제한다 |
| `POST /api/fcm/token` | 위와 같다 |

### 🟠 2순위 — 틀리면 권한 검사가 엉뚱한 사람으로 돈다

| 엔드포인트 | 위험 |
|---|---|
| `PATCH` · `DELETE /api/posts/{postId}` | 작성자 본인 검사의 기준 |
| `PATCH` · `DELETE /api/comments/{commentId}` | 같음 |
| `POST /api/posts/{postId}/comments` | 작성자가 뒤바뀐 채 저장된다 |
| `DELETE /api/chat-rooms/{roomId}/members/{targetUserId}` | 방장 검사의 기준 |
| `PATCH /api/chat-rooms/{roomId}/name` | 같음 |
| `POST /api/follows` · `DELETE /api/follows/{followingId}` | 팔로우 주체가 뒤바뀐다 |

### 🟡 3순위 — 틀리면 500이 난다 (운영에서 금방 드러난다)

`POST /api/chat-rooms/group` · `POST /api/chat-rooms/{roomId}/members` ·
`DELETE /api/chat-rooms/{roomId}/members/me` · `DELETE /auth/logout` ·
`GET /api/posts/{postId}/comments` · `POST /api/comments/{commentId}/emojis` ·
`DELETE /api/comments/{commentId}/emojis/{emojiType}` · `DELETE /api/follows/requests/{followId}`

> 3순위도 가치가 없지는 않다. 다만 **타입 실수는 §2-1이 이미 전수로 막고 있으므로**,
> 여기 남은 것은 "인자 순서" 같은 좁은 실수뿐이다. 1·2순위를 먼저 채운다.

---

## 5. 합의가 필요한 것 (#204의 나머지)

이 문서는 **현황과 우선순위**까지다. 아래는 팀이 정해야 한다.

- [ ] **신규 엔드포인트는 컨트롤러 경유 테스트 1개를 요구할 것인가**
      — 요구한다면 PR 템플릿 체크리스트에 넣는 것이 가장 싸다
- [ ] 위 1·2순위 12개를 언제 채울 것인가 (Sprint 5 안 / Sprint 6 이월)
- [ ] 3순위는 채우지 않기로 하고 §2-1에 맡길 것인가

### 참고 — 비용

`AuthenticatedEndpointSliceTest`는 6개 엔드포인트에 **약 180줄**이다. 엔드포인트당 25~30줄이고
`@WebMvcTest` 슬라이스라 컨텍스트도 가볍다(6개 테스트 5초). 1·2순위 12개를 채우는 데
300~400줄 정도로 잡으면 된다.

---

## 6. 이 표를 다시 만들려면

수작업으로 세지 않는다. 컨트롤러에서 매핑을 뽑고 테스트에서 `mockMvc.perform(...)` 경로를 뽑아
대조하는 방식이다. 생성 스크립트는 이 PR의 논의에 남겨 뒀다.

주의할 점 두 가지가 있었다.

1. `@GetMapping`과 메서드 사이에 `@Operation`이 끼어 있어, 시그니처를 찾을 때 **어노테이션의 괄호를
   메서드 파라미터로 오인**하기 쉽다. `public` 키워드를 먼저 찾아야 한다
2. `@RequestMapping`에 슬래시가 없는 컨트롤러가 있어 경로가 어긋났다 (이 PR에서 수정)
