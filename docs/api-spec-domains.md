# memorIN API 명세서 — 도메인 API (유저 / 게시물 / 댓글 / 이모지 / 팔로우 / 알림 / 채팅)

> 최신 기준 문서: 2026-09-15 (#182 게시물 좋아요 복구 반영)
>
> 이전 갱신: 2026-09-14 (Sprint 5 W11 — 구현 대조 갱신) · 2026-08-20 (Sprint 3 W8)
>
> 이 문서는 `docs/api-spec.md`(인증 · 미디어)의 **후속 도메인 명세**다. 노션 전체 API 명세 페이지에서는 미디어 API 다음, 환경 변수 앞에 이어 붙인다.
>
> Notion API 명세서에 남아 있는 이전 주제/초안 내용은 잔재일 수 있다. 최신 명세는 이 레포의 `docs/` 문서를 기준으로 확인한다.

## 0. 이 문서의 구현 상태

Sprint 0 시점 이 문서는 도메인 API 전부가 "엔티티만 있고 컨트롤러는 없음"이었다.
2026-09-14 기준으로 **채팅까지 전부 구현돼 있다.** 아래 표는 코드를 직접 대조해 갱신했다.

| 도메인 | 컨트롤러 | 엔드포인트 | 이 문서 상태 |
|---|---|---:|---|
| 유저 / 프로필 | `UserController` | 5 | 구현 반영 (프로필 수정은 **미구현** — §5-3 · #217) |
| 게시물 | `PostController` | 8 | 구현 반영. **검색 신설**(§6-7, #199) |
| 댓글 | `PostCommentController` | 4 | 구현 반영 |
| 댓글 이모지(반응) | `CommentEmojiController` | 3 | 구현 반영 (§8-5) |
| 팔로우 | `FollowController` | 5 | 구현 반영. 받은 요청 거절 경로 신설(#174, §9-4) |
| 알림 | `NotificationController` | 3 | 구현 반영. **생성 트리거·발송 연결 완료**(§11) |
| Web Push 구독 | `WebPushSubscriptionController` | 2 | 구현 반영 (§11-1, #192) |
| **채팅방** | `ChatRoomController` | **8** | 구현 반영 (§10-2~§10-5·§10-7, #193·#215) |
| **채팅 메시지** | `MessageController` | **1** (REST) + STOMP 2 | 구현 반영 (§10-1·§10-6, #190·#201·#209) |
| 인증 | `AuthController` | 4 | `docs/api-spec.md` §3. **로그아웃 신설**(#184) |
| 미디어 | `MediaController` | 4 | `docs/api-spec.md` §4 |
| FCM 토큰 | `FcmTokenController` | 2 | `docs/api-spec.md` |
| 게시물 좋아요 | `PostLikeController` | 2 | 구현 반영 — #148에서 제거했다가 #182에서 복구(§7) |

REST 합계 **50개** (+ STOMP 발행 목적지 2개). **정본(live)은 Swagger UI**(`/swagger-ui/index.html`)다. 이 문서는 Swagger가 자동 생성하지
못하는 것 — 요청 예시, 실패 케이스, 정책 배경, **알려진 결함** — 을 보충한다.

`OpenApiDocsTest`가 모든 엔드포인트에 `@Operation(summary)`와 `@Tag`가 붙어 있는지 검증한다.
명세 없는 API가 머지되면 CI가 막으므로, 이 표가 다시 통째로 낡는 일은 없어야 한다.

> 공통 규칙(Base URL, Content-Type, 인증 헤더, 공통 응답 봉투 `{success, data, error}`)은 `docs/api-spec.md` §2를 따른다.
> 이 문서는 그 위에 **페이지네이션 규칙(§2-5)** 과 **봉투 예외 현황(§2-6)** 을 보강한다.

## 2-5. 목록 응답 규칙 (페이지네이션 · 공통 규칙 보강)

목록 조회 API는 커서 기반 페이지네이션을 사용한다. UUID v7이 시간순 정렬 가능하므로 커서 = 마지막 항목의 `id`로 둔다.

요청 쿼리 파라미터:

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | string(uuid) | X | 없음(최신부터) | 이 id **이전(더 오래된)** 항목부터 조회 |
| `size` | number | X | `20` | 페이지 크기(최대 50) |

응답 `data` 공통 구조:

```json
{
  "success": true,
  "data": {
    "items": [],
    "nextCursor": "0198f2a1-...",
    "hasNext": true
  },
  "error": null
}
```

`nextCursor`는 다음 요청의 `cursor`로 그대로 사용한다. `hasNext=false`면 마지막 페이지다.

## 2-6. 공통 응답 봉투를 쓰지 않는 엔드포인트 (현황)

`docs/api-spec.md` §2-4는 응답을 `{success, data, error}` 봉투로 감싼다고 규정하지만,
실제로는 **47개 중 15개가 DTO를 그대로 반환한다.** FE가 엔드포인트마다 파싱을 분기해야 하므로 현황을 명시한다.

| 엔드포인트 | 실제 반환 | 비고 |
|---|---|---|
| `DELETE /auth/logout` | `204 No Content` | 본문 없음 |
| **`/api/chat-rooms/**` 7개 전부** | 각 DTO / 본문 없음 | **#203** — 채팅방 생성·목록·초대·강퇴·나가기·이름변경 |
| **`GET /api/posts/search`** | `PostListResponse` | **#203** — 같은 컨트롤러의 나머지 7개는 봉투를 쓴다 |
| `POST /api/comments/{commentId}/emojis` | `EmojiToggleResponse` | §8-5 |
| `GET /api/comments/{commentId}/emojis` | `List<EmojiSummary>` | §8-5 |
| `DELETE /api/comments/{commentId}/emojis/{emojiType}` | `204 No Content` | 본문 없음 — 의도된 설계 |
| 미디어 API 4개 | 각 DTO | `docs/api-spec.md` §2-4에 이미 예외로 기록됨 |

> `GET /api/users/{userId}`는 #178에서 봉투를 적용해 이 목록에서 빠졌다(#165 해소).
> 반대로 Sprint 4에 들어온 채팅방·검색 8개가 새로 들어왔다.

STOMP로 내려가는 메시지(`/topic/rooms/{roomId}`)는 이 규약의 대상이 아니다 — HTTP 응답이 아니다.

봉투로 통일하면 FE 파싱이 전부 바뀌는 **파괴적 변경**이라 스프린트 경계에서 한 번에 처리해야 한다. → §14

---

## 5. 유저 / 프로필 API

### 5-1. 내 프로필 조회

```http
GET /api/users/me
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.** `GET /api/users/me` — 응답은 `username`·`displayName`·`bio` 3개뿐이다(이메일·프로필 이미지 없음).

#### 설명

토큰의 `sub`(userId)에 해당하는 로그인 사용자 본인의 프로필을 반환한다. `email`은 본인 조회에서만 포함한다.

#### 인증

필요

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "0198f2a1-8b3c-7def-9012-3456789abcde",
    "email": "user@example.com",
    "username": "daily_user",
    "displayName": "Daily User",
    "bio": "매일 기록합니다",
    "profileImageKey": "uploads/2026/07/01/{uuid}/profile.jpg",
    "createdAt": "2026-07-01T12:00:00Z"
  },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료/위조 |
| 404 | `MEMBER_001` | 토큰의 사용자가 존재하지 않음(탈퇴 등) |

### 5-2. 유저 프로필 조회 (공개)

```http
GET /api/users/{userId}
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#162, 2026-08-11). 다만 아래 "알려진 결함" 3건이 미해결이다.

#### 설명

다른 사용자의 공개 프로필을 조회한다. `email`·비밀번호 등 민감 필드는 응답에 없다.
팔로워 수·팔로잉 수 집계는 포함하지 않는다(목록 API로 따로 조회).

#### 인증

필요

#### 경로 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `userId` | string(uuid) | 조회 대상 사용자 id |

#### 응답

Status: `200 OK` — **공통 봉투 없이 DTO를 그대로 반환한다**(§2-6).

```json
{
  "userId": "0198f2a1-8b3c-7def-9012-3456789abcde",
  "username": "daily_user",
  "displayName": "Daily User",
  "profileImage": "uploads/2026/07/01/{uuid}/profile.jpg",
  "bio": "매일 기록합니다"
}
```

- `userId`는 UUID **문자열**이다(다른 API는 uuid 타입 그대로 내려간다).
- `profileImage`는 **MinIO object key**다. 화면에 그리려면 미디어 다운로드 presigned URL이 따로 필요하다.

#### 알려진 결함 (Sprint 3 미해결)

| 이슈 | 내용 |
|---|---|
| #164 | 탈퇴(`deleted_at`) 사용자도 그대로 조회된다. 유저 검색은 2026-08-14에 `deleted_at` 필터를 넣었는데 이 API만 빠져 있다 |
| #165 | 공통 응답 봉투 미적용 + 이미지 키를 URL 변환 없이 그대로 내려준다 |

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `USER_001` | 존재하지 않는 사용자 |

### 5-3. 내 프로필 수정

```http
PATCH /api/users/me
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

**미구현 (설계 초안).** `UserController`에 이 매핑이 없다 — 호출하면 404다.
프로필 이미지 등록 경로가 없어 §5-2의 `profileImage`는 현재 회원가입 시점 값에서 바뀌지 않는다.

#### 설명

본인 프로필의 표시명·자기소개·프로필 이미지를 수정한다. 이메일/비밀번호/username 변경은 별도 API로 분리한다(정책 미정). `profileImageKey`는 presigned 업로드로 먼저 올린 뒤 그 `objectKey`를 전달한다.

#### 인증

필요

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `displayName` | string | X | 최대 100자 | 화면 표시명 |
| `bio` | string | X | 최대 길이 정책 미정 | 자기소개 |
| `profileImageKey` | string | X | 최대 500자 | MinIO object key |

> 부분 수정(PATCH): 전달된 필드만 갱신한다. 전부 생략 시 변경 없음.

예시:

```json
{
  "displayName": "새 표시명",
  "bio": "소개 문구를 바꿨어요",
  "profileImageKey": "uploads/2026/07/14/{uuid}/new-profile.jpg"
}
```

#### 응답

Status: `200 OK` — 수정된 프로필을 5-1과 동일 스키마로 반환한다.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | 필드 길이 등 검증 실패 |
| 401 | `AUTH_001` | 인증 누락/만료 |

---

## 6. 게시물 API

게시물 본문은 `content` **블록 배열(JSONB)** 이다. 텍스트·이미지·비디오 블록을 순서대로 담는다. 이미지/비디오 블록의 `fileKey`는 presigned 업로드(`docs/api-spec.md` §4)로 먼저 올린 뒤의 object key다.

블록 예시:

```json
[
  { "type": "text", "value": "오늘의 기록" },
  { "type": "image", "fileKey": "uploads/2026/07/14/{uuid}/photo.jpg" },
  { "type": "video", "fileKey": "uploads/2026/07/14/{uuid}/clip.mp4" }
]
```

### 6-1. 게시물 생성

```http
POST /api/posts
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

**구현됨.**

#### 설명

로그인 사용자의 게시물을 생성한다. 작성자(`userId`)는 토큰에서 결정하며 요청 body로 받지 않는다. `mediaKeys`로 전달된 파일들을 `post_media`에 순서대로 연결한다.

#### 인증

필요

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `content` | array | O | 최소 1개 블록 | 본문 블록 배열(JSONB) |
| `visibility` | string(enum) | X | `PUBLIC`\|`FRIENDS`\|`PRIVATE` | 공개 범위, 기본 `PUBLIC` |
| `recordedDate` | string(date) | X | `YYYY-MM-DD` | 기록 날짜, 기본 오늘 |
| `mediaKeys` | array(string) | X | 각 원소 blank 불가 | 첨부 미디어 object key 목록(표시 순서) |

예시:

```json
{
  "content": [
    { "type": "text", "value": "오늘의 기록" },
    { "type": "image", "fileKey": "uploads/2026/07/14/{uuid}/photo.jpg" }
  ],
  "visibility": "FRIENDS",
  "recordedDate": "2026-07-14",
  "mediaKeys": ["uploads/2026/07/14/{uuid}/photo.jpg"]
}
```

#### 응답

Status: `201 Created` — 생성된 게시물을 6-2 스키마로 반환한다.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | `content` 누락/빈 배열, `visibility` 잘못된 값 |
| 401 | `AUTH_001` | 인증 누락/만료 |

### 6-2. 게시물 단건 조회

```http
GET /api/posts/{postId}
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.** 공개범위 판정 자체는 비로그인(`requesterId = null`)까지 지원하지만,
`SecurityConfig`가 `anyRequest().authenticated()`라 **토큰 없이 호출하면 401**이다.
비로그인 열람을 실제로 열려면 보안 설정에서 별도로 허용해야 한다 → §14

#### 설명

게시물 1건을 작성자 요약·미디어 목록과 함께 조회한다. `visibility`에 따라 접근 권한을 확인한다(`PRIVATE`=본인만, `FRIENDS`=수락된 팔로워, `PUBLIC`=전체). 조회 시 `viewCount` 증가 정책은 구현 시 확정한다.

#### 인증

필요

#### 경로 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `postId` | string(uuid) | 게시물 id |

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "0198f2b0-...",
    "author": {
      "id": "0198f2a1-...",
      "username": "daily_user",
      "displayName": "Daily User",
      "profileImageKey": "uploads/.../profile.jpg"
    },
    "content": [
      { "type": "text", "value": "오늘의 기록" },
      { "type": "image", "fileKey": "uploads/.../photo.jpg" }
    ],
    "media": [
      {
        "fileKey": "uploads/.../photo.jpg",
        "mimeType": "image/jpeg",
        "fileSizeBytes": 1048576,
        "orderIndex": 0,
        "width": 1080,
        "height": 1080,
        "durationSec": 0
      }
    ],
    "visibility": "FRIENDS",
    "recordedDate": "2026-07-14",
    "viewCount": 12,
    "likeCount": 3,
    "commentCount": 2,
    "liked": false,
    "createdAt": "2026-07-14T09:00:00Z",
    "updatedAt": "2026-07-14T09:00:00Z"
  },
  "error": null
}
```

> `likeCount`/`commentCount`/`liked`는 집계 필드다. 성능(N+1) 이슈가 있어 목록 조회에서의 포함 방식은 구현 시 확정한다.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `POST_002` | 비공개/친구공개 게시물 접근 권한 없음 |
| 404 | `POST_001` | 존재하지 않거나 삭제된 게시물 |

### 6-3. 게시물 목록(피드) 조회

```http
GET /api/posts?cursor={uuid}&size=20&userId={uuid}
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.** `from`/`to` 날짜 범위 필터 포함(캘린더 뷰용).

#### 설명

게시물 목록을 최신순으로 조회한다. 필터에 따라 전체 피드 / 특정 유저 게시물 / 팔로잉 피드를 구분한다. 페이지네이션은 §2-5를 따른다.

#### 인증

필요

#### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `cursor` | string(uuid) | X | §2-5 커서 |
| `size` | number | X | §2-5 페이지 크기 |
| `userId` | string(uuid) | X | 특정 유저의 게시물만 조회 |
| `scope` | string(enum) | X | `ALL`\|`FOLLOWING`, 기본 `ALL` — **미구현**(친구 피드는 `GET /api/posts/friends` 별도 경로) |
| `from` | string(date) | X | `recorded_date` 시작일(포함), `yyyy-MM-dd` — 캘린더 뷰용 |
| `to` | string(date) | X | `recorded_date` 종료일(포함), `yyyy-MM-dd` |

캘린더에서 특정 하루를 탭한 경우 `from`과 `to`에 같은 날짜를 준다 (`?from=2026-08-11&to=2026-08-11`).
범위 필터는 커서 페이지네이션과 함께 동작한다 — 페이지를 넘겨도 범위 밖 게시물은 섞이지 않는다.

#### 응답

Status: `200 OK` — `data.items[]`는 6-2 스키마의 요약 형태다(집계 필드 포함 여부는 구현 시 확정).

```json
{
  "success": true,
  "data": {
    "items": [ { "id": "0198f2b0-...", "author": { }, "content": [ ], "createdAt": "2026-07-14T09:00:00Z" } ],
    "nextCursor": "0198f2af-...",
    "hasNext": true
  },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | `size` 범위 초과, `cursor` 형식 오류, `from`이 `to`보다 늦음, 날짜 형식 오류 |
| 401 | `AUTH_001` | 인증 누락/만료 |

### 6-4. 게시물 수정

```http
PATCH /api/posts/{postId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

**구현됨.**

#### 설명

본인 게시물의 본문·공개범위를 수정한다. 전달된 필드만 갱신한다.

#### 인증

필요 (작성자 본인만)

#### 요청 Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `content` | array | X | 본문 블록 배열 |
| `visibility` | string(enum) | X | `PUBLIC`\|`FRIENDS`\|`PRIVATE` |
| `mediaKeys` | array(string) | X | 첨부 미디어 재구성(전달 시 전체 교체) |

#### 응답

Status: `200 OK` — 수정된 게시물을 6-2 스키마로 반환한다.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | 검증 실패 |
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `POST_002` | 작성자 아님 |
| 404 | `POST_001` | 없거나 삭제된 게시물 |

### 6-5. 게시물 삭제

```http
DELETE /api/posts/{postId}
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.**

#### 설명

본인 게시물을 삭제한다. `deleted_at`을 채우는 **소프트 삭제**다(ERD 결정 사항). 첨부 미디어의 물리 파일 GC는 media/infra 도메인 정책에 따른다.

#### 인증

필요 (작성자 본인만)

#### 응답

Status: `200 OK`

```json
{ "success": true, "data": null, "error": null }
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `POST_002` | 작성자 아님 |
| 404 | `POST_001` | 없거나 이미 삭제된 게시물 |

### 6-6. 추천 피드 조회

```http
GET /api/posts/recommend?cursor={cursor}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#148, 2026-08-20). 서비스 코드는 있었으나 노출 경로가 없어 호출할 수 없던 것을 엔드포인트로 열었다.

#### 설명

최근 **14일 내 전체공개** 게시물을 후보(최대 300건)로 모아 점수순으로 정렬한다.

```
engagement = 1 + 좋아요수 × 3 + 댓글수 × 2 + 조회수 × 0.1
score      = log(engagement) / (경과시간h + 2)^1.6
```

- 분모가 시간이라 **오래될수록 점수가 내려간다**(새 글이 자연스럽게 위로 온다).
- 정렬 기준은 `score DESC`, 동점이면 `postId DESC`.
- 좋아요 수는 #182에서 다시 반영했다(§7). 댓글 수와 동일하게 후보 게시물 ID를 모아 한 번의 `IN` 조회로 배치 조회한다 — 게시물마다 조회하면 N+1이다.

#### 커서

`cursor`에는 **첫 요청의 기준 시각(asOf)** 이 함께 담긴다. 페이지를 넘기는 동안 새 글이 올라와도
목록이 밀리거나 중복되지 않는다. 직전 응답의 `nextCursor`를 그대로 넣는다.

#### 응답

Status: `200 OK` — 구조는 §6-3 목록 응답과 같다(`items` · `nextCursor` · `hasNext`).
`items[].attachments`에 첨부 미디어가 presigned 다운로드 URL과 함께 들어간다.

`size`는 기본 20 · 최대 50이다.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |

> 후보 조회는 `idx_posts_reco (created_at DESC) WHERE deleted_at IS NULL AND visibility = 'PUBLIC'` 부분 인덱스를 탄다.
> 미디어는 게시물마다 조회하지 않고 한 번의 `IN` 조회로 붙인다 — `RecommendedFeedQueryTest`가 쿼리 수를 고정한다(미디어 3배 → SQL 3개 불변).

---

### 6-7. 게시물 검색 (키워드 · 태그 · 시간대)

```http
GET /api/posts/search?keyword=제주&tags=TRAVEL&tags=FOOD&timeslot=AM&sort=ACCURACY_DESC&cursor={cursor}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

구현됨 (#181 → #199, 2026-09-05).

⚠️ 이 엔드포인트만 **전역 응답 봉투를 쓰지 않는다.** 같은 컨트롤러의 나머지 6개는 쓴다 → §2-6 · #203

#### 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | string | X | `content` 원문에 대한 부분 일치(대소문자 무시) |
| `tags` | `TagType[]` | X | **최대 3개.** 반복 지정(`?tags=A&tags=B`) |
| `timeslot` | `AM` \| `PM` | X | 기록 시간대 |
| `sort` | enum | X | 기본 `LATEST` |
| `cursor` · `size` | | X | §2-5 공통 규칙. `size` 기본 20 · 최대 50 |

`TagType`: `STUDY` · `GAME` · `ANIMAL` · `TRAVEL` · `EXERCISE` · `FOOD` · `MUSIC` · `DAILY` · `HOBBY` · `ETC`

#### 정렬

| `sort` | 기준 |
|---|---|
| `LATEST` (기본) | `recorded_date DESC, id DESC` |
| `VIEW_COUNT_DESC` | 조회수 내림차순 |
| `ACCURACY_DESC` | **정확도** — 키워드 등장 횟수 + 일치한 태그 수 |

`ACCURACY_DESC`는 `keyword`나 `tags` 중 **하나는 있어야** 한다. 둘 다 없으면 점수를 계산할 기준이 없다.

> **태그 매칭 규칙이 정렬에 따라 다르다.** `LATEST`·`VIEW_COUNT_DESC`는 `tags`를 **전부 포함**하는
> 게시물만(`@>`), `ACCURACY_DESC`는 **하나라도 겹치면** 후보에 넣고 겹친 개수를 점수로 쓴다.

#### 공개 범위

검색 결과는 `PostAccessPolicy`와 **같은 판정**을 따른다.

- `PUBLIC` — 전체 공개
- 본인 글 — `visibility`와 무관하게 항상 보임
- `FRIENDS` — **양방향 `ACCEPTED` 팔로우**가 있어야 보임

> 초기 구현은 `FRIENDS`를 아예 제외해 친구가 친구공개로 올린 글이 검색에서 통째로 사라졌다.
> 단건 조회와 판정이 갈리는 것은 #141이 정리한 원칙 위반이라 #199에서 맞췄다.

#### 응답

`data`는 §6-3(게시물 목록)과 같은 `PostListResponse` 구조다 — **봉투만 없다.**

```json
{
  "items": [
    {
      "postId": "0198f1a2-...",
      "authorId": "0198f2a3-...",
      "content": "[{\"type\":\"text\",\"text\":\"제주 여행 1일차\"}]",
      "visibility": "PUBLIC",
      "timeslot": "AM",
      "recordedDate": "2026-08-11",
      "viewCount": 12,
      "attachments": [],
      "tagTypes": ["TRAVEL", "FOOD"]
    }
  ],
  "nextCursor": "eyJyZWNvcmRlZERhdGUiOi...",
  "hasNext": true
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `POST_003` | `tags`가 4개 이상 |
| 400 | `COMMON_002` | `cursor` 형식 오류, enum 값 오타 |
| 401 | `AUTH_001` | 인증 누락/만료 |

#### 성능 메모

`keyword` 검색은 `LOWER(content::text) LIKE '%kw%'`다. 선행 와일드카드라 B-tree로는 가속되지 않아
`V11__add_content_trgm_index.sql`이 `pg_trgm` GIN 인덱스를 건다. **인덱스 표현식과 쿼리 표현식이
정확히 일치해야** 플래너가 탄다.

> `CREATE EXTENSION pg_trgm`은 슈퍼유저 권한이 필요하다. 운영 DB 롤을 분리해 두었다면
> 이 마이그레이션이 멈출 수 있다 → #221

---

## 7. 좋아요 API (#182, 2026-09-15 복구)

```http
POST /api/posts/{postId}/likes
GET  /api/posts/{postId}/likes
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#182). 2026-08-11에 반응 모델을 댓글 이모지로 단일화하며(#145) 게시물 좋아요를 미채택으로
정리했었고(#148), 이후 요구사항이 다시 생겨 되살렸다. `PostLikes` · `PostLikeRepository` ·
`PostLikeService`는 삭제 전 커밋에서 그대로 복원했고, 컨트롤러/DTO는 이번에 새로 작성했다
(과거에도 컨트롤러는 존재한 적이 없다). 반응 채널이 두 개가 됐다 — 게시물 단위는 좋아요, 댓글 단위는
이모지(§8-5)다.

#### 토글 — `POST`

이미 눌렀으면 취소, 안 눌렀으면 등록한다(요청 Body 없음). 취소는 게시물 접근 권한과 무관하게 항상
가능하다 — 팔로우를 끊거나 게시물 공개범위가 바뀌어도 과거에 누른 좋아요는 뗄 수 있다. 등록만
`PostAccessPolicy`로 접근 권한을 검사한다.

Status: `200 OK`

```json
{ "success": true, "data": { "liked": true, "likeCount": 12 }, "error": null }
```

`liked`는 토글 후 최종 상태다. 동시 더블탭은 멱등 처리한다(`liked=true` 유지) — 같은 게시물에 대한
동시 "처음 누르기" 요청은 게시물 행 잠금(`PostRepository.findByIdForUpdate`, `PESSIMISTIC_WRITE`)으로
직렬화해 `uq_post_like` 위반을 막는다. 예외를 잡아 삼키는 방식은 쓰지 않는다 — Postgres는 제약 위반이
나면 트랜잭션 전체를 abort 상태로 만들어, 잡아도 커밋 시점에 실패할 수 있다.

#### 집계 조회 — `GET`

Status: `200 OK` — 응답 형태는 `POST`와 동일(`liked` · `likeCount`). 접근 권한이 없으면 `POST_002`다.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `POST_002` | 접근 권한 없는 게시물(비공개 등)에 처음 좋아요를 시도 |
| 404 | `POST_001` | 존재하지 않는 게시물 |

#### 추천 피드 반영

`likeCount * 3` 항을 점수 공식에 되살렸다(§6-6). 후보 게시물 ID를 모아 댓글 수와 동일하게
한 번의 `IN` 조회로 배치 집계한다 — 게시물마다 조회하면 N+1이다(`RecommendedFeedQueryTest`).

좋아요를 등록하면 게시물 작성자에게 `LIKE` 알림을 남긴다. `referenceId`는 게시물 id다. 자기 게시물
좋아요는 알림을 만들지 않으며, 취소해도 이미 만든 알림은 유지한다. 같은 사람이 같은 게시물에 좋아요를
다시 등록해도 최초 알림 한 건만 유지한다.

---

## 8. 댓글 API

`post_comments`는 `parent_id`로 **1단계 대댓글**을 지원한다(ERD 결정). `parentId`가 있으면 대댓글, 없으면 최상위 댓글이다.

### 8-1. 댓글 작성

```http
POST /api/posts/{postId}/comments
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

**구현됨** (#111).

#### 설명

게시물에 댓글 또는 대댓글을 작성한다. 작성자는 토큰에서 결정한다.

#### 인증

필요

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `body` | string | O | blank 불가, 최대 길이 정책 미정 | 댓글 내용 |
| `parentId` | string(uuid) | X | 유효한 댓글 id | 대댓글일 때 부모 댓글 id |

예시:

```json
{ "body": "좋은 기록이네요!", "parentId": null }
```

#### 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": {
    "commentId": "0198f2c0-...",
    "authorId": "0198f2a1-...",
    "authorUsername": "daily_user",
    "authorDisplayName": "Daily User",
    "authorProfileImageKey": "uploads/.../profile.jpg",
    "body": "좋은 기록이네요!",
    "deleted": false,
    "parentId": null,
    "createdAt": "2026-08-20T09:10:00",
    "emojis": []
  },
  "error": null
}
```

작성자 정보는 **중첩 객체가 아니라 `author*` 평면 필드**다. `postId`는 응답에 없다(요청 경로에 있으므로).

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | `body` 누락/공백 |
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `POST_001` | 없거나 삭제된 게시물 |
| 404 | `COMMENT_001` | `parentId`가 존재하지 않는 댓글 |

### 8-2. 댓글 스레드 조회

```http
GET /api/posts/{postId}/comments
Authorization: Bearer {accessToken}   # 선택
```

#### 상태

**구현됨** (#111 · 응답 보강 #145).

#### 설명

게시물의 댓글·대댓글을 **평면 목록 + `parentId`** 로 반환한다(트리 중첩 아님). 대댓글은 1단계까지다.
비로그인도 호출할 수 있다 — 이때 `emojis[].reactedByMe`는 전부 `false`다.

> **⚠️ 페이지네이션이 없다.** §2-5 커서 규칙을 따르지 않고 해당 게시물의 댓글을 **전부** 반환한다.
> `cursor`·`size`를 보내도 무시된다. 댓글이 많은 게시물에서 응답이 무한정 커지는 구조라 → §14

#### 응답

Status: `200 OK` — `data`가 페이지 객체가 아니라 **배열**이다.

```json
{
  "success": true,
  "data": [
    {
      "commentId": "0198f2c0-...",
      "authorId": "0198f2a1-...",
      "authorUsername": "daily_user",
      "authorDisplayName": "Daily User",
      "authorProfileImageKey": "uploads/.../profile.jpg",
      "body": "좋은 기록이네요!",
      "deleted": false,
      "parentId": null,
      "createdAt": "2026-08-20T09:10:00",
      "emojis": [ { "emojiType": "HEART", "count": 2, "reactedByMe": true } ]
    },
    {
      "commentId": "0198f2c1-...",
      "authorId": null,
      "authorUsername": null,
      "authorDisplayName": null,
      "authorProfileImageKey": null,
      "body": "삭제된 댓글입니다.",
      "deleted": true,
      "parentId": "0198f2c0-...",
      "createdAt": "2026-08-20T09:12:00",
      "emojis": []
    }
  ],
  "error": null
}
```

- **삭제된 댓글(tombstone)은 작성자 정보를 전부 `null`로 감춘다.** 본문만 지우고 닉네임을 남기면
  "누가 지웠는지"가 그대로 노출되기 때문이다. 대댓글이 달린 부모를 지워도 스레드 구조는 유지된다.
- `emojis`는 배치 집계 결과다. 댓글마다 §8-5를 따로 부르면 N+1이 된다
  (`CommentThreadQueryCountTest`가 쿼리 수를 고정한다).

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | (인증 헤더를 보냈는데) 만료·위조 |
| 404 | `POST_001` | 없거나 삭제된 게시물 |

### 8-3. 댓글 수정

```http
PATCH /api/comments/{commentId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

**구현됨.** 이전 리비전 문서에 아예 빠져 있던 엔드포인트다.

#### 요청 Body

```json
{ "body": "고쳐 씁니다" }
```

#### 응답

Status: `200 OK` — 수정된 댓글을 §8-1과 같은 스키마로 반환한다(`emojis`는 빈 배열).

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | `body` 누락/공백 |
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `COMMENT_006` | 삭제된 댓글은 수정 불가 |
| 404 | `COMMENT_001` | 존재하지 않는 댓글 |

### 8-4. 댓글 삭제

```http
DELETE /api/comments/{commentId}
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.**

#### 설명

본인 댓글을 삭제한다. `deleted_at`을 채우는 소프트 삭제다.
대댓글이 달린 부모를 지우면 본문이 `"삭제된 댓글입니다."` 로 바뀌고 작성자 정보는 `null`이 된다(§8-2).

> 이 소프트 삭제가 `body` NOT NULL 제약에 걸려 항상 500이던 결함이 있었다(2026-08-11 수정, `V5__allow_null_comment_body.sql`).

#### 인증

필요 (작성자 본인만)

#### 응답

Status: `200 OK`

```json
{ "success": true, "data": null, "error": null }
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `COMMENT_002` | 작성자 아님 |
| 404 | `COMMENT_001` | 없거나 이미 삭제된 댓글 |

### 8-5. 댓글 이모지(반응)

```http
POST   /api/comments/{commentId}/emojis
DELETE /api/comments/{commentId}/emojis/{emojiType}
GET    /api/comments/{commentId}/emojis
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#138 · #145). 반응은 **댓글에만** 붙는다(§7).

#### 이모지 종류

`emojiType`은 enum이다: `HEART` · `DISLIKE` · `LIKE` · `NO` · `CHECK` · `FIRE`.
(여기의 `LIKE`는 이모지 한 종류일 뿐, 폐기된 게시물 좋아요와 무관하다.)

#### 토글 — `POST`

같은 이모지를 다시 누르면 취소된다. 요청 Body는 `{ "emojiType": "HEART" }`.

Status: `200 OK` — **봉투 없이 DTO 직접 반환**(§2-6).

```json
{ "emojiType": "HEART", "added": true }
```

`added=true`면 추가, `false`면 취소다. 동시에 두 번 눌려 유니크 제약에 걸리면
`added=true`로 멱등 처리한다(에러를 던지지 않는다).

#### 삭제 — `DELETE`

내가 단 이모지를 명시적으로 제거한다. 달지 않은 상태여도 `204 No Content`다(멱등).

#### 집계 조회 — `GET`

Status: `200 OK` — 배열 직접 반환.

```json
[
  { "emojiType": "HEART", "count": 3, "reactedByMe": true },
  { "emojiType": "FIRE",  "count": 1, "reactedByMe": false }
]
```

> **댓글 스레드 조회(§8-2)에 이미 같은 집계가 포함된다.** 스레드를 그리는 화면에서는
> 이 API를 댓글 수만큼 추가 호출하지 말 것 — 정확히 그 N+1을 막으려고 배치 집계를 넣었다
> (`docs/n+1-audit.md` §5).

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `COMMENT_EMOJI_001` | 삭제된 댓글에 이모지 시도 |
| 404 | `COMMENT_001` | 존재하지 않는 댓글 |

---

## 9. 팔로우 API

`follows`는 `follower_id → following_id` **단방향** 관계이며 `(follower_id, following_id)` 유니크 제약과 `status` enum(`PENDING`·`ACCEPTED`·`BLOCKED`)을 가진다. 비공개/친구공개 계정은 `PENDING`으로 요청 후 수락 시 `ACCEPTED`가 된다. 맞팔(친구) 여부는 양방향 `ACCEPTED`로 앱에서 판정한다(ERD 결정).

### 9-1. 팔로우 / 팔로우 요청

```http
POST /api/follows
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

**구현됨.**

#### 설명

`followingId` 대상을 팔로우한다. `follower_id`는 토큰에서 결정한다. 대상 계정 공개 정책에 따라 초기 `status`가 `ACCEPTED`(공개) 또는 `PENDING`(요청 필요)로 결정된다. 자기 자신 팔로우는 금지(DDL `follower_id <> following_id`).

#### 인증

필요

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `followingId` | string(uuid) | O | 본인 id 불가 | 팔로우할 대상 사용자 id |

#### 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": { "id": "0198f2d0-...", "followingId": "0198f2a2-...", "status": "PENDING", "createdAt": "2026-07-14T09:20:00Z" },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `FOLLOW_002` | 자기 자신 팔로우 시도 |
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `MEMBER_001` | 대상 사용자 없음 |
| 409 | `FOLLOW_003` | 이미 팔로우/요청한 관계 |

### 9-2. 팔로우 취소 / 언팔로우

```http
DELETE /api/follows/{followingId}
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.** #174에서 "받은 요청 거절"과 경로가 분리됐다.

#### 설명

path의 `followingId`는 **내가 팔로우한(또는 내가 요청을 보낸) 상대**다.
서버는 `(follower = 나, following = path)` 행을 찾아 삭제한다. 즉 이 API가 처리하는 것은 두 가지다.

- 내가 보낸 `PENDING` 요청 취소
- 이미 맺어진 `ACCEPTED` 관계 해제(언팔로우)

**받은 요청을 거절하는 용도가 아니다.** 그건 방향이 반대라 §9-4를 쓴다.

> 2026-08-20 이전에는 이 API 하나가 "거절"까지 담당하는 것으로 문서화돼 있었으나,
> 받은 요청은 `(follower = 상대, following = 나)` 방향이라 행을 찾지 못해 항상 404였다(#163).
> #174에서 거절 전용 경로를 신설해 해결했다.

#### 인증

필요

#### 응답

Status: `200 OK`

```json
{ "success": true, "data": null, "error": null }
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `FOLLOW_001` | 그 방향의 팔로우 관계가 없음 (없는 관계에 멱등하지 않다) |

### 9-3. 팔로우 요청 수락

```http
PATCH /api/follows/{followId}/accept
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.** 메서드는 `POST`가 아니라 **`PATCH`** 다(이전 리비전 오기 정정).

#### 설명

나에게 온 `PENDING` 팔로우 요청을 수락해 `ACCEPTED`로 바꾼다. 요청 대상(`following_id`)이 본인일 때만 가능하다.
`followId`는 §9-7 목록 응답의 `followId`를 그대로 쓴다. 거절은 §9-4다.

#### 인증

필요 (요청의 `following_id` 본인만)

#### 응답

Status: `200 OK`

```json
{ "success": true, "data": null, "error": null }
```

> 실제 구현은 **본문 없이 성공만 반환한다.** 갱신된 관계를 화면에 그리려면 목록을 다시 조회한다.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `FOLLOW_004` | 내게 온 요청이 아님 |
| 404 | `FOLLOW_001` | 존재하지 않는 팔로우 요청 |

### 9-4. 받은 팔로우 요청 거절

```http
DELETE /api/follows/requests/{followId}
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#174, 2026-08-20). #163 수정으로 신설된 경로다.

#### 설명

나에게 온 `PENDING` 요청을 거절한다. path의 `followId`는 **팔로우 행의 id**이며,
§9-7 목록 응답의 `followId`를 그대로 넘긴다(수락 §9-3과 같은 값).

수락과 거절이 같은 식별자를 쓰고, 취소/언팔로우(§9-2)만 상대 사용자 id를 쓴다.

- 요청의 **수신자 본인**만 거절할 수 있다(아니면 403 `FOLLOW_004`).
- 이미 `ACCEPTED`가 된 관계는 거절 대상이 아니다(409 `FOLLOW_005`). 이 경우 §9-2로 해제한다.

#### 응답

Status: `200 OK`

```json
{ "success": true, "data": null, "error": null }
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `FOLLOW_004` | 내게 온 요청이 아님 |
| 404 | `FOLLOW_001` | 존재하지 않는 팔로우 요청 |
| 409 | `FOLLOW_005` | PENDING 상태의 팔로우 요청이 아님 |

### 9-5. 팔로워 목록 조회

```http
GET /api/users/{userId}/followers?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.** 커서 페이징 인덱스 최적화 완료(`docs/n+1-audit.md` §6).

#### 설명

해당 유저를 팔로우하는(=`following_id = userId`, `status = ACCEPTED`) 사용자 목록을 조회한다. 페이지네이션은 §2-5를 따른다.

#### 인증

필요

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "items": [ { "id": "0198f2a3-...", "username": "friend_a", "displayName": "Friend A", "profileImageKey": "uploads/.../a.jpg" } ],
    "nextCursor": "0198f2a2-...",
    "hasNext": false
  },
  "error": null
}
```

### 9-6. 팔로잉 목록 조회

```http
GET /api/users/{userId}/followings?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨.** 커서 페이징 인덱스 최적화 완료(`docs/n+1-audit.md` §6).

#### 설명

해당 유저가 팔로우하는(=`follower_id = userId`, `status = ACCEPTED`) 사용자 목록을 조회한다. 응답 스키마는 §9-5와 동일하다.

#### 인증

필요

### 9-7. 받은 팔로우 요청 목록

```http
GET /api/follows/requests?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#136, #218). 소셜 탐색 화면에서 "나에게 온 요청"을 그릴 때 쓴다.

#### 설명

내게 온 `PENDING` 요청을 최신순(`id DESC`)으로 커서 페이지네이션해 반환한다. 요청을 보낸 사람(`follower`)의 요약 정보가 함께 온다. `cursor`와 `size`는 §2-5를 따른다.

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "followId": "0198f2d0-...",
        "userId": "0198f2a3-...",
        "username": "friend_a",
        "displayName": "Friend A",
        "bio": "안녕하세요"
      }
    ],
    "nextCursor": "0198f2d0-...",
    "hasNext": true
  },
  "error": null
}
```

`nextCursor`를 다음 요청의 `cursor`로 전달한다. `hasNext=false`면 마지막 페이지다. `followId`를 수락(§9-3)·거절(§9-4)에 그대로 넘긴다.

`JOIN FETCH`로 요청자를 함께 조회하므로 N+1은 없다.

---

## 10. 채팅 API (STOMP + REST)

채팅은 **실시간 전송은 WebSocket/STOMP**, **방·멤버·메시지 관리는 REST**로 나눈다.

- 현재 구현: **완료.** 방 관리(#193) · 텍스트/공유 메시지(#190·#169) · 메시지 히스토리(#201) · CONNECT 인증과 SUBSCRIBE 인가(#209) · 읽음 처리(§10-7, #215)
- 아직 없는 것: 메시지 이모지(#196)
- ⚠️ `ChatRoomController`의 8개 엔드포인트 중 7개는 **전역 응답 봉투를 쓰지 않는다.** DTO를 그대로 반환한다 → §2-6 · #203
  읽음 처리(§10-7)만 예외로 `ApiResponse<T>` 봉투를 쓴다 — #215가 명시적으로 요구해서 이 엔드포인트만 다르다.

### 10-1. 실시간 메시지 (STOMP)

#### 연결

```
SockJS 엔드포인트: /ws
```

`/ws`는 `SecurityConfig`에서 `permitAll`이다. **핸드셰이크는 열려 있고 실제 인증은 CONNECT 프레임에서 한다.**
HTTP 필터는 핸드셰이크 1회만 지나가므로 이후 STOMP 프레임을 지킬 수 없고, SockJS 폴백은
핸드셰이크에 `Authorization` 헤더를 싣지 못하기 때문이다.

CONNECT 프레임에 토큰을 **네이티브 헤더**로 실어야 한다.

```js
// stomp.js
beforeConnect: async () => {
  const token = await resolveAccessToken();
  client.connectHeaders = { Authorization: `Bearer ${token}` };
}
```

| 상황 | 결과 |
|---|---|
| `Authorization` 헤더 없음 / `Bearer ` 접두사 아님 | 연결 거부 |
| 만료·서명 불일치·탈퇴 사용자 토큰 | 연결 거부 (원인은 클라이언트에 노출하지 않는다) |
| 연결만 하고 CONNECT를 보내지 않음 | 30초 후 서버가 정리(`setTimeToFirstMessage`) |

#### 목적지(destination)

| 방향 | 목적지 | 설명 |
|---|---|---|
| 구독 (SUBSCRIBE) | `/topic/rooms/{roomId}` | 그 방의 메시지를 수신 |
| 발행 (SEND) | `/app/chat.sendText` | 텍스트 메시지 전송 |
| 발행 (SEND) | `/app/chat.sharePost` | 게시물 공유 메시지 전송 |

`roomId`는 UUID다. 발행 목적지에는 `roomId`가 들어가지 않고 **payload에 담는다.**

#### 인가 — 인증과 별개다

CONNECT에서 "누구인지"를 확인하고, 그 뒤 **"이 방을 볼 자격이 있는지"를 따로** 본다.

| 시점 | 검사 |
|---|---|
| SUBSCRIBE | 그 방의 **활성 멤버**인가 (`left_at IS NULL`). 아니면 구독 거부 |
| SEND | `MessageService`가 매 요청 활성 멤버인지 확인. 아니면 `CHAT_ROOM_MEMBERS_001` |
| 배달 직전 | 지금도 활성 멤버인가. 아니면 그 세션으로만 배달 취소 (#210) |

마지막 항목이 필요한 이유: SUBSCRIBE 검사는 구독을 *요청하는 순간*만 본다.
이미 구독을 맺은 뒤 강퇴당하면 그 구독은 살아 있으므로, 배달 시점에 한 번 더 확인한다.

> 나갔거나 강퇴당한 사람도 `roomId`를 알고 있다. 그래서 멤버 행의 **존재**가 아니라
> **활성 여부**(`left_at IS NULL`)를 봐야 한다.

#### 발행 payload

`/app/chat.sendText`

```json
{ "roomId": "0198f2e0-...", "text": "안녕하세요" }
```

`/app/chat.sharePost`

```json
{ "roomId": "0198f2e0-...", "postId": "0198f1a2-..." }
```

#### 수신 payload

구독자는 `/topic/rooms/{roomId}`로 아래를 받는다. **전역 응답 봉투를 쓰지 않는다** — STOMP 프레임이라 HTTP 응답 규약과 별개다.

```json
{
  "id": "0198f3b1-...",
  "roomId": "0198f2e0-...",
  "senderId": "0198f2a3-...",
  "type": "TEXT",
  "content": { "type": "TEXT", "text": "안녕하세요" },
  "sentAt": "2026-09-07T14:03:11"
}
```

`content`는 `type` 필드로 구분되는 다형 객체다.

| `type` | `content` 형태 |
|---|---|
| `TEXT` | `{ "type": "TEXT", "text": "..." }` |
| `POST_SHARE` | `{ "type": "POST_SHARE", "postId": "uuid" }` |
| `IMAGE` | 타입은 정의돼 있으나 **발신 경로가 아직 없다** |

`POST_SHARE`는 **`postId`만** 담는다. 게시물 본문을 복사해 넣지 않으므로, 카드 렌더링에 필요한
내용은 수신 측이 `GET /api/posts/{postId}`로 따로 조회한다. 공유 시점에 발신자의 열람 권한을
검사하지만(`PostAccessPolicy`), 수신자의 권한은 조회 시점에 다시 검사된다.

#### 저장 순서

메시지는 **DB에 저장되고 커밋된 뒤에** 브로드캐스트된다. 반대로 하면 저장이 실패했는데
화면에는 남는 메시지가 생긴다(`docs/sprint4-architecture-review.md` §5).

#### 브로커 제한

| 항목 | 값 | 이유 |
|---|---|---|
| 하트비트 | 10초 / 10초 | 죽은 연결을 서버가 알아채는 유일한 수단 |
| 세션당 미전송 버퍼 | 512KB | 느린 수신자가 힙을 먹는 것을 막는다 |
| 인바운드 프레임 크기 | 64KB | 프레임 하나가 힙을 크게 먹는 것을 막는다 |
| 전송 시간 제한 | 10초 | 초과 시 세션을 끊는다 |

실측은 `docs/ws-stress-test.md`에 있다. InMemory 브로커라 **새는 곳은 전부 우리 힙**이다.

---

### 10-2. 1:1 채팅방 생성

```http
POST /api/chat-rooms/direct
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "targetUserId": "0198f2a3-..." }
```

이미 두 사람의 활성 1:1 방이 있으면 **새로 만들지 않고 기존 방을 반환**한다.
요청 방향이 반대여도(상대가 먼저 걸었어도) 같은 방으로 잡힌다.

Status: `200 OK` — **봉투 없음**(#203)

```json
{ "roomId": "0198f2e0-...", "type": "DIRECT", "name": null, "newlyCreated": true }
```

`newlyCreated`가 `false`면 기존 방을 돌려준 것이다. `DIRECT` 방은 이름이 없다.

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `CHAT_ROOMS_002` | 자기 자신과 1:1 방 생성 시도 |
| 404 | `USER_001` | 상대를 찾을 수 없음 |

### 10-3. 그룹 채팅방 생성

```http
POST /api/chat-rooms/group
Content-Type: application/json

{ "name": "스터디방", "memberIds": ["0198f2a3-...", "0198f2a4-..."] }
```

요청자가 `OWNER`가 되고 `memberIds`는 `MEMBER`로 들어간다. `memberIds`에 요청자가 섞여 있으면 건너뛴다.
중복 UUID는 걸러낸다.

| 제약 | 값 |
|---|---|
| `name` | 필수, 최대 100자 |
| `memberIds` | 필수(비어 있을 수 없음), 최대 100명 |

응답은 §10-2와 같은 형태(`type`이 `GROUP`).

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | 이름 누락·길이 초과, `memberIds` 비어 있음·초과 |
| 404 | `USER_001` | 초대 대상 중 하나라도 없으면 **전체 실패** |

### 10-4. 내 채팅방 목록

```http
GET /api/chat-rooms
Authorization: Bearer {accessToken}
```

내가 **활성 멤버인** 방만, 참여 시각 역순으로 반환한다. 나간 방은 나오지 않는다.

Status: `200 OK` — **봉투 없음**(#203), **페이지네이션 없음**

```json
[
  {
    "roomId": "0198f2e0-...", "type": "GROUP", "name": "스터디방", "myRole": "OWNER",
    "unreadCount": 12,
    "lastMessage": { "preview": "안녕하세요", "sentAt": "2026-09-07T14:03:11" }
  },
  {
    "roomId": "0198f2e1-...", "type": "DIRECT", "name": null, "myRole": "MEMBER",
    "unreadCount": 0,
    "lastMessage": null
  }
]
```

`unreadCount`는 `GREATEST(lastReadAt, joinedAt)` 이후 도착한, **내가 보내지 않은** 메시지 수다(#258).
`lastMessage`는 그 방에 메시지가 한 번도 없으면 `null`. `preview`는 `TEXT`면 본문, `IMAGE`면
`"사진"`, `POST_SHARE`면 `"게시물을 공유했습니다"` 고정 문구다 — `content`(jsonb) 파싱 없이
`type` 컬럼만으로 분기한다.

> 목록 API 중 커서 페이지네이션을 쓰지 않는 둘 중 하나다(다른 하나는 댓글 스레드 §8-2).
> 방 개수가 많아지면 재검토 대상이다.

### 10-5. 채팅방 멤버 관리

전부 **그룹 방 전용**이다. 1:1 방에 호출하면 `400 CHAT_ROOMS_002`.

| 동작 | 경로 | 권한 |
|---|---|---|
| 초대 | `POST /api/chat-rooms/{roomId}/members` | 활성 멤버 누구나 |
| 강퇴 | `DELETE /api/chat-rooms/{roomId}/members/{targetUserId}` | **방장만** |
| 나가기 | `DELETE /api/chat-rooms/{roomId}/members/me` | 본인 |
| 이름 변경 | `PATCH /api/chat-rooms/{roomId}/name` | **방장만** |

초대 요청 본문은 `{ "memberIds": ["uuid", ...] }`(최대 100명, 중복 제거).
이름 변경은 `{ "name": "새 이름" }`(최대 100자).

네 엔드포인트 모두 성공 시 **본문이 없다.** 봉투도 쓰지 않는다(#203).

#### 동작 규칙

- **나갔던 사람을 다시 초대**하면 새 행을 만들지 않고 기존 행을 되살린다
  (`chat_room_members`에 `(room_id, user_id)` 유니크 제약이 있다)
- **방장이 나가면** 남은 멤버 중 가장 먼저 들어온 사람이 방장이 된다
- **강퇴는 "타인에 의한 나가기"로 구현돼 있다.** 강퇴당한 사람을 다시 초대할 수 있다 — 재입장 차단은 미구현(`ChatRoomController`의 TODO)

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `CHAT_ROOMS_002` | 1:1 방에 그룹 기능 호출, 방장이 아님, 자기 자신 강퇴 |
| 404 | `CHAT_ROOMS_001` | 방이 없음 |
| 404 | `CHAT_ROOM_MEMBERS_001` | 대상이 그 방의 활성 멤버가 아님 |

### 10-6. 메시지 히스토리 조회

```http
GET /api/chat-rooms/{roomId}/messages?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

**최신순**으로 반환한다. 무한 스크롤로 과거를 더 불러올 때 `nextCursor`를 그대로 `cursor`에 넣는다.
`size` 기본 20 · 최대 50.

그 방의 **활성 멤버만** 조회할 수 있다.

Status: `200 OK` — 이쪽은 **봉투를 쓴다**

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "0198f3b1-...",
        "roomId": "0198f2e0-...",
        "senderId": "0198f2a3-...",
        "type": "TEXT",
        "content": { "type": "TEXT", "text": "안녕하세요" },
        "sentAt": "2026-09-07T14:03:11"
      }
    ],
    "nextCursor": "0198f3b0-...",
    "hasNext": true
  },
  "error": null
}
```

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 404 | `CHAT_ROOMS_001` | 방이 없음 |
| 404 | `CHAT_ROOM_MEMBERS_001` | 활성 멤버가 아님 |

### 10-7. 읽음 처리

```http
POST /api/chat-rooms/{roomId}/read
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#215). `chat_room_members.last_read_at`은 입장 시각으로 한 번 채워진 뒤
갱신하는 코드가 없었다 — 이 엔드포인트가 호출 시점으로 값을 갱신하는 유일한 경로다.
이 컨트롤러의 다른 엔드포인트와 달리 **`ApiResponse<T>` 공통 봉투를 쓴다**(§2-6 예외).

#### 설명

호출 시점을 `lastReadAt`으로 기록한다. DIRECT·GROUP 채팅방 모두 대상이다.
활성 멤버(나간 사람 제외)만 호출할 수 있다.

#### 응답

Status: `200 OK`

```json
{ "success": true, "data": null, "error": null }
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `CHAT_ROOMS_001` | 방이 없음 |
| 404 | `CHAT_ROOM_MEMBERS_001` | 활성 멤버가 아님(나갔거나 강퇴당함) |

> "안 읽은 메시지 N개"는 §10-4(`GET /api/chat-rooms`)의 `unreadCount`로 구현됐다(#258).
> 이 엔드포인트 자체는 `lastReadAt` 갱신만 하고, 계산은 방 목록 조회 쪽 책임이다.

---

## 11. 알림 API

```http
GET   /api/notifications?cursor={uuid}&size=20
PATCH /api/notifications/{notificationId}/read
PATCH /api/notifications/read-all
Authorization: Bearer {accessToken}
```

#### 상태

조회·읽음 처리(#168)와 생성 트리거(#186), FCM·Web Push 발송(#192·#211·#213)이 구현됐다.

#### 발생 지점 — 어디서 알림이 만들어지나

| 타입 | 발생 지점 |
|---|---|
| `FOLLOW_REQUEST` | `FollowService` — 팔로우 요청 |
| `FOLLOW_ACCEPTED` | `FollowService` — 요청 수락 |
| `COMMENT` | `PostCommentService` — 댓글 작성 |
| `LIKE` | `PostLikeService` — 게시물 좋아요 등록 |
| `MESSAGE` | `MessageService` — 텍스트·게시물 공유 메시지 발신 |

Sprint 3 결산 §4가 "호출부 0개 — 항상 빈 배열"로 적었던 구멍은 #186에서 메워졌다.

채팅 메시지는 발신자를 제외한 활성(`leftAt IS NULL`) 방 멤버마다 알림을 하나씩 저장한다. `saveAll`로 한 번에 적재하며, 알림 히스토리는 접속 상태와 무관하게 남는다.

#### 저장 → 발송 파이프라인

```
NotificationService.save()
  → 이벤트 발행(PushNotificationRequested)
  → @TransactionalEventListener(AFTER_COMMIT) + @Async
  → FcmPushService / WebPushService
```

**커밋 이후에 발송한다.** 팔로우가 롤백됐는데 알림만 나가는 상황을 막기 위해서다
(`docs/sprint4-architecture-review.md` §9). 발송 실패는 로그만 남기고 본 기능 트랜잭션에 영향을 주지 않는다.

두 발송 경로 모두 **기본 비활성**이다(`FIREBASE_ENABLED` · `WEB_PUSH_ENABLED`). 꺼도 알림은 DB에 저장되고
조회 API로 보인다. 발송만 일어나지 않는다.

수신자에게 인증된 STOMP 세션이 하나라도 있으면 WebSocket이 실시간 전달을 담당하므로 FCM·Web Push는 발송하지 않는다.

#### 목록 조회

`size` 기본 20 · 최대 50(`normalizeSize`). 커서는 직전 응답의 `nextCursor`를 그대로 넣는다.

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "0198f3a0-...",
        "type": "FOLLOW_REQUEST",
        "actorId": "0198f2a3-...",
        "actorUsername": "friend_a",
        "actorDisplayName": "Friend A",
        "title": "새 팔로우 요청",
        "message": "Friend A님이 팔로우를 요청했습니다",
        "referenceId": "0198f2d0-...",
        "read": false,
        "createdAt": "2026-08-19T14:03:11"
      }
    ],
    "nextCursor": "0198f3a0-...",
    "hasNext": true
  },
  "error": null
}
```

| 필드 | 설명 |
|---|---|
| `type` | `FOLLOW_REQUEST` · `FOLLOW_ACCEPTED` · `COMMENT` · `LIKE` · `MESSAGE` |
| `actor*` | 알림을 발생시킨 사람. 시스템 알림이면 셋 다 `null` |
| `referenceId` | 이동 대상 id. `FOLLOW_REQUEST`·`FOLLOW_ACCEPTED`는 팔로우 행 id, `COMMENT`는 댓글 id, `LIKE`는 게시물 id, `MESSAGE`는 **채팅방 id**다. FE는 `MESSAGE` 알림 탭 시 해당 방으로 이동한다. |
| `read` | 읽음 여부 |


#### 읽음 처리

- `PATCH /api/notifications/{notificationId}/read` — 내 알림이 아니면 `404 NOTIFICATION_001`
- `PATCH /api/notifications/read-all` — 내 알림 전체 읽음. 항상 `200`

응답은 둘 다 `{ "success": true, "data": null, "error": null }`.

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `NOTIFICATION_001` | 없거나 내 것이 아닌 알림 |

---

### 11-1. Web Push 구독 등록 / 해제

브라우저 Push 구독 정보를 서버에 보관한다. FCM(앱)과 별개 경로다.

```http
POST   /api/web-push/subscriptions
DELETE /api/web-push/subscriptions
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 등록

```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "p256dh": "BN4GvZ...",
  "auth": "tBHI..."
}
```

세 값 모두 브라우저 `PushManager.subscribe()` 결과에서 그대로 꺼내 보낸다.
같은 `endpoint`로 다시 등록하면 갱신된다(기기마다 `endpoint`가 다르므로 여러 개를 가질 수 있다).

#### 해제

```json
{ "endpoint": "https://fcm.googleapis.com/fcm/send/..." }
```

로그아웃하거나 브라우저 알림 권한을 끌 때 호출한다.

#### 응답

둘 다 `{ "success": true, "data": null, "error": null }`.

#### 서버 설정

발송에는 VAPID 키 쌍이 필요하다(`WEB_PUSH_VAPID_PUBLIC_KEY` · `WEB_PUSH_VAPID_PRIVATE_KEY` ·
`WEB_PUSH_VAPID_SUBJECT`). **공개 키는 FE의 `PushManager.subscribe()` 호출에도 같은 값이 들어가야 한다.**
`WEB_PUSH_ENABLED=false`(기본)면 구독 등록은 되지만 발송은 일어나지 않는다.

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | `endpoint`·`p256dh`·`auth` 누락 |
| 401 | `AUTH_001` | 인증 누락/만료 |

---

## 12. 에러코드 (구현 현황)

이전 리비전의 "추가 예정" 표는 전부 반영됐다. 아래는 `global/common/ErrorCode.java`의 **실제 정의**다(2026-08-20).

| 코드 | HTTP | 메시지 요지 |
|---|---:|---|
| `COMMON_001` | 500 | 서버 내부 오류 |
| `COMMON_002` | 400 | 요청 값이 올바르지 않음 (검증 실패·잘못된 날짜 범위 등) |
| `COMMON_003` | 403 | 권한 없음 |
| `COMMON_004` | 404 | 경로 없음 |
| `COMMON_005` | 405 | 허용되지 않는 메서드 |
| `AUTH_001` | 401 | 인증 필요 |
| `AUTH_002` | 401 | 이메일 또는 비밀번호 불일치 |
| `AUTH_003` | 401 | 재로그인 필요(만료) |
| `AUTH_004` | 401 | 유효하지 않은 인증 정보 |
| `USER_001` | 404 | 존재하지 않는 회원 |
| `USER_002` | 409 | 이미 사용 중인 이메일 |
| `USER_003` | 409 | 이미 사용 중인 이름 |
| `FOLLOW_001` | 404 | 존재하지 않는 팔로우 관계 |
| `FOLLOW_002` | 400 | 자기 자신 팔로우 |
| `FOLLOW_003` | 409 | 이미 존재하는 팔로우 관계 |
| `FOLLOW_004` | 403 | 내게 온 요청이 아님 |
| `FOLLOW_005` | 409 | PENDING 상태의 팔로우 요청이 아님 |
| `POST_001` | 404 | 존재하지 않는 게시물 |
| `POST_002` | 403 | 게시물 접근 권한 없음 |
| `POST_003` | 400 | 잘못된 cursor 값 |
| `COMMENT_001` | 404 | 존재하지 않는 댓글 |
| `COMMENT_002` | 403 | 본인만 삭제 가능 |
| `COMMENT_003` | 404 | 부모 댓글 없음 |
| `COMMENT_004` | 400 | 대댓글에 답글 불가(1단계 제한) |
| `COMMENT_005` | 403 | 비공개 게시물에 댓글 불가 |
| `COMMENT_006` | 403 | 삭제된 댓글 수정 불가 |
| `COMMENT_EMOJI_001` | 403 | 삭제된 댓글에 이모지 불가 |
| `MEDIA_001` ~ `MEDIA_007` | 400·403·404·500 | 미디어/쿼터 관련 — `docs/api-spec.md` §4 |
| `NOTIFICATION_001` | 404 | 알림 없음 |

**아직 없는 것:** 채팅용 `CHAT_001`(방 없음)·`CHAT_002`(멤버 아님). Sprint 4 채팅 구현 시 추가한다.

> 사용자 조회 실패는 `USER_001`, 팔로우 관계 조회 실패는 `FOLLOW_001`, PENDING 상태 불일치는 `FOLLOW_005`로 구분한다.

## 13. 관련 문서

- `docs/api-spec.md`: 인증(JWT)·미디어 Presigned Upload·업로드 커밋 API. 이 문서와 함께 전체 API 명세를 구성한다.
- `docs/erd.md`: 테이블 스키마·설계 결정. 요청/응답 필드 근거.
- `docs/domain-interface-draft.md`: 도메인 간 경계(참조 방식·유저 엔티티 단일화 등). 미확정 항목이 API 스키마에 영향.
- `docs/storage-quota-policy.md`: 구현된 Storage Quota 정책.
- `docs/n+1-audit.md`: 목록/피드 API의 쿼리 수·인덱스 실측 기록. 페이지네이션 설계 근거(§6).
- `docs/sprint2-wrapup.md`: 반응 모델을 댓글 이모지로 단일화한 결정(§6) 등 스프린트 결산.
- `docs/db-migration-guide.md`: Flyway 마이그레이션 규칙. **적용된 마이그레이션 파일은 수정하지 않는다.**

## 14. 열린 결정 사항

2026-09-14(Sprint 5 W11) 기준으로 남은 것만 적는다. 해결된 항목은 본문에 반영했다.

| # | 항목 | 왜 지금 정해야 하나 | 이슈 |
|---|---|---|---|
| 1 | **공통 응답 봉투 통일 여부**(§2-6) | 15개 엔드포인트가 DTO를 직접 반환한다. Sprint 4에 채팅방·검색 8개가 새로 들어와 **오히려 늘었다.** FE 연동 본격화 전이 가장 싸다 | #203 |
| 2 | **`profileImage` 키 → URL 변환 주체**(§5-2) | 서버가 presigned URL로 바꿔 줄지, FE가 미디어 API를 한 번 더 부를지 | — |
| 4 | **`referenceId`의 타입별 의미**(§11) | FE가 알림 탭 시 어디로 보낼지 판단하려면 타입별 규약이 필요하다. 채팅 메시지 알림이 붙으면 더 필요해진다 | #216 |
| 5 | **댓글 스레드 페이지네이션**(§8-2) | 목록 API 중 전체를 반환하는 둘 중 하나다 (다른 하나는 채팅방 목록 §10-4) | #225 |
| 6 | **`content` JSONB 구조 스키마·크기** | #199가 태그를 `content` 안이 아니라 별도 `posts.tags` 컬럼으로 빼면서 "태그 키" 문제는 우회됐다. 그러나 `@ValidJson`은 여전히 "유효한 JSON"만 보고 **구조도 크기도 검증하지 않는다** | #244 |
| 8 | **비로그인 열람 허용 여부**(§6-2) | 서비스는 비로그인 공개글 조회를 지원하는데 보안 설정이 전부 막고 있다. 오픈소스 배포로 인스턴스가 늘면 "둘러보기"를 열지 말지가 실제 선택이 된다 | — |
| 9 | **채팅방 목록 페이지네이션**(§10-4) | 방 개수가 많아지면 전체 반환이 문제가 된다 | — |
| 10 | **강퇴당한 사람의 재입장 차단**(§10-5) | 현재 강퇴는 "타인에 의한 나가기"라 다시 초대할 수 있다. 차단이 필요한 개념인지부터 정해야 한다 | — |

### 이번 갱신에서 닫힌 항목

| 이전 # | 항목 | 결말 |
|---|---|---|
| 1 | `POST /auth/refresh` 응답 봉투 | `ApiResponse<LoginResponse>`로 통일. FE 파싱 변경 사항은 `docs/fe-api-change-notice.md`에 기록 |
| 3 | `GET /api/follows/requests` 페이지네이션 | `cursor`·`size`와 `{items, nextCursor, hasNext}` 응답으로 전환 |
| 7 | `FOLLOW_001` 의미 분리 | 사용자 없음은 `USER_001`, 관계 없음은 `FOLLOW_001`, 상태 불일치는 `FOLLOW_005` |
| 4 | 알림 저장 트리거 위치 | **이벤트 + `AFTER_COMMIT`으로 결정**(#186·#192). §11에 반영 |
| 7 | `content` JSONB 태그 키 표준 | 별도 `tags` 컬럼으로 **우회**(#199). 구조 스키마 문제는 위 6번으로 남았다 |
| 10 | 채팅 REST/STOMP 경계 | REST는 `/api/chat-rooms/**`, 실시간은 `/app`·`/topic`으로 갈렸다. §10에 반영 |

이전 리비전의 "유저 엔티티 단일화 / 도메인 참조 방식 / 엔티티 필드 네이밍"은 구현이 이미 한 방향으로 굳었다.
남은 것은 문서 정합성 정리(#41)이며, 이 표에서는 제외했다.
