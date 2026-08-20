# memorIN API 명세서 — 도메인 API (유저 / 게시물 / 댓글 / 이모지 / 팔로우 / 알림 / 채팅)

> 최신 기준 문서: 2026-08-20 (Sprint 3 W8 — 구현 대조 갱신)
>
> 이 문서는 `docs/api-spec.md`(인증 · 미디어)의 **후속 도메인 명세**다. 노션 전체 API 명세 페이지에서는 미디어 API 다음, 환경 변수 앞에 이어 붙인다.
>
> Notion API 명세서에 남아 있는 이전 주제/초안 내용은 잔재일 수 있다. 최신 명세는 이 레포의 `docs/` 문서를 기준으로 확인한다.

## 0. 이 문서의 구현 상태

Sprint 0 시점 이 문서는 도메인 API 전부가 "엔티티만 있고 컨트롤러는 없음"이었다.
2026-08-20 기준으로 **채팅을 빼면 모두 구현돼 있다.** 아래 표는 코드를 직접 대조해 갱신했다.

| 도메인 | 컨트롤러 | 엔드포인트 | 이 문서 상태 |
|---|---|---:|---|
| 유저 / 프로필 | `UserController` | 5 | 구현 반영 (프로필 수정은 **미구현** — §5-3) |
| 게시물 | `PostController` | 7 | 구현 반영 (추천 피드 §6-6 노출) |
| 댓글 | `PostCommentController` | 4 | 구현 반영 |
| 댓글 이모지(반응) | `CommentEmojiController` | 3 | 구현 반영 (§8-5) |
| 팔로우 | `FollowController` | 5 | 구현 반영. 받은 요청 거절 경로 신설(#174, §9-4) |
| 알림 | `NotificationController` | 3 | 구현 반영 (§11) |
| 인증 | `AuthController` | 3 | `docs/api-spec.md` §3 |
| 미디어 | `MediaController` | 4 | `docs/api-spec.md` §4 |
| FCM 토큰 | `FcmTokenController` | 1 | `docs/api-spec.md` |
| 게시물 좋아요 | 없음 | 0 | **미채택** — 반응은 댓글 이모지로 단일화. 자바 코드 제거 완료(§7) |
| 채팅 | 없음(엔티티만) | 0 | 설계 초안 · Sprint 4 (§10) |

합계 **35개**. **정본(live)은 Swagger UI**(`/swagger-ui/index.html`)다. 이 문서는 Swagger가 자동 생성하지
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
실제로는 **35개 중 8개가 DTO를 그대로 반환한다.** FE가 엔드포인트마다 파싱을 분기해야 하므로 현황을 명시한다.

| 엔드포인트 | 실제 반환 | 비고 |
|---|---|---|
| `POST /auth/refresh` | `LoginResponse` | 같은 컨트롤러의 signup·login은 봉투를 쓴다 |
| `GET /api/users/{userId}` | `UserProfileResponse` | 이슈 #165 |
| `POST /api/comments/{commentId}/emojis` | `EmojiToggleResponse` | §8-5 |
| `GET /api/comments/{commentId}/emojis` | `List<EmojiSummary>` | §8-5 |
| `DELETE /api/comments/{commentId}/emojis/{emojiType}` | `204 No Content` | 본문 없음 — 의도된 설계 |
| 미디어 API 4개 | 각 DTO | `docs/api-spec.md` §2-4에 이미 예외로 기록됨 |

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
engagement = 1 + 댓글수 × 2 + 조회수 × 0.1
score      = log(engagement) / (경과시간h + 2)^1.6
```

- 분모가 시간이라 **오래될수록 점수가 내려간다**(새 글이 자연스럽게 위로 온다).
- 정렬 기준은 `score DESC`, 동점이면 `postId DESC`.
- 게시물 좋아요는 미채택이므로(§7) 점수에 반영되지 않는다. 게시물 단위 반응이 생기면 항을 되살린다.

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

## 7. 좋아요 API — 미채택 (Sprint 2 결정)

**이 API는 만들지 않는다.** 2026-08-11에 반응 모델을 **댓글 이모지 하나로 단일화**하기로 확정했다(#145,
`docs/sprint2-wrapup.md` §6). 게시물에 붙는 좋아요/반응 테이블(`post_emoji`)은 신설하지 않았다.

- 엔드포인트: 없음. `POST/DELETE /api/posts/{postId}/likes`는 **구현된 적이 없다.**
- 이전 리비전의 7-1·7-2 설계 초안은 이 결정으로 폐기했다.
- 실제 반응 API는 **§8-5 댓글 이모지**다.

### 정리 결과 (#148, 2026-08-20)

`PostLikes` 엔티티 · `PostLikeRepository` · `PostLikeService`를 **삭제했다.** 어떤 컨트롤러에도 연결되지
않은 채 스프린트 두 개를 넘어온 코드였다.

추천 피드 점수 공식에 있던 `likeCount * 3` 항도 함께 걷어냈다 — 입력이 영구히 0이라 계산에 기여하지 않았다(§6-6).

**DB는 이번 스프린트에서 건드리지 않는다.** `post_likes` 테이블과 `idx_post_likes_post` 인덱스는 그대로 둔다.
스키마 변경은 PM 승인이 필요하다는 Sprint 0 게이트 규칙이 있고, 되돌리려면 또 다른 마이그레이션이 필요한
파괴적 변경이기 때문이다. 테이블 드롭은 별도 안건으로 올린다.

> `NotificationType.LIKE`(§11)도 이 결정으로 쓰이지 않는 값이 됐다. 알림 도메인 정리 시 함께 판단한다.

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
- 이미 `ACCEPTED`가 된 관계는 거절 대상이 아니다(404 `FOLLOW_001`). 이 경우 §9-2로 해제한다.

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
| 404 | `FOLLOW_001` | 존재하지 않거나 PENDING이 아닌 요청 |

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
GET /api/follows/requests
Authorization: Bearer {accessToken}
```

#### 상태

**구현됨** (#136). 소셜 탐색 화면에서 "나에게 온 요청"을 그릴 때 쓴다.

#### 설명

내게 온 `PENDING` 요청을 최신순(`id DESC`)으로 반환한다. 요청을 보낸 사람(`follower`)의 요약 정보가 함께 온다.

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "followId": "0198f2d0-...",
      "userId": "0198f2a3-...",
      "username": "friend_a",
      "displayName": "Friend A",
      "bio": "안녕하세요"
    }
  ],
  "error": null
}
```

`followId`를 수락(§9-3)·거절(§9-4)에 그대로 넘긴다.

#### 제약

- **페이지네이션이 없다.** 요청이 쌓이면 전부 내려간다. 팔로워/팔로잉 목록(§9-5·§9-6)은 커서 페이징으로
  전환했지만 이 API는 남아 있다 → §14
- `JOIN FETCH`로 요청자를 함께 조회하므로 N+1은 없다.

---

## 10. 채팅 API (STOMP + REST)

채팅은 **실시간 전송은 WebSocket/STOMP**, **방·멤버·메시지 관리는 REST**로 나눈다.

- 현재 구현: **없음(엔티티만).** 초기 `ChatController`/`ChatMessage` 에코 프로토타입은 STOMP 브로커 설정이 없어 배선되지 않은 죽은 코드였으므로 삭제했다. 실제 채팅 도메인은 Sprint 4다.
- **진행 중:** 게시물 공유 API PR([#169](https://github.com/inu-appcenter/MermorIN/pull/169))이 `MessageController`·`WebSocketConfig`·메시지 content 타입(`TextContent`/`ImageContent`/`PostShareContent`)을 함께 들고 온다. 머지되면 이 절을 다시 갱신한다(2026-08-20 기준 미머지).
- REST(방 생성/목록/메시지 조회)는 **설계 초안**이다.
- 인증: `SecurityConfig`에 `/ws/**`, `/*.html` `permitAll` 매처가 남아 있으나 대응하는 엔드포인트가 없어 현재는 무효하다. WebSocket 토큰 인증은 채팅 착수 시 함께 설계한다(`docs/auth-jwt-design.md` §6).

### 10-1. 실시간 메시지 (STOMP) — 미구현(설계 초안)

#### 연결

```
SockJS 엔드포인트: /ws
```

클라이언트는 `/ws`로 SockJS 연결 후 STOMP 세션을 맺는다(현재 `src/main/resources/static/test.html` 기준).

#### 목적지(destination)

| 방향 | 목적지 | 설명 |
|---|---|---|
| 구독 (SUBSCRIBE) | `/sub/chat/room/{roomId}` | 해당 방의 메시지를 수신 |
| 발행 (SEND) | `/pub/chat/room/{roomId}` | 해당 방으로 메시지 전송 |

#### 메시지 payload

```json
{
  "roomId": "1",
  "sender": "daily_user",
  "content": "안녕하세요"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | string | 방 식별자 (현재 에코 테스트는 문자열/Long) |
| `sender` | string | 보낸 사람 (현재 클라이언트 지정, 인증 도입 시 토큰 기반으로 교체) |
| `content` | string | 메시지 내용 (실제 도메인에서는 JSONB 블록으로 확장 예정) |

#### 현재 동작 / 예정

- **현재**: `/pub/chat/room/{roomId}`로 받은 메시지를 `/sub/chat/room/{roomId}` 구독자에게 그대로 브로드캐스트(에코). DB 저장·인증·멤버십 검사 없음.
- **예정**: 전송 시 `messages` 테이블 저장, 발신자 토큰 검증, 방 멤버십 검사, `content`를 JSONB 블록으로 확장, `chat_room_members.last_read_at` 갱신.

### 10-2. 채팅방 생성

```http
POST /api/chat/rooms
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

설계 초안. `ChatRooms`/`ChatRoomMembers` 엔티티 구현됨, REST API 미구현.

#### 설명

`DIRECT`(1:1) 또는 `GROUP` 채팅방을 만든다. 생성자는 `OWNER`, 초대된 사용자는 `MEMBER`로 `chat_room_members`에 추가한다. `DIRECT`는 두 사용자 간 중복 방 생성을 막는 정책을 둔다(구현 시 확정).

#### 인증

필요

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `type` | string(enum) | O | `DIRECT`\|`GROUP` | 방 종류 |
| `memberIds` | array(uuid) | O | `DIRECT`는 1명 | 초대할 사용자 id(생성자 제외) |
| `name` | string | X | 최대 100자 | 방 이름(`GROUP` 권장, `DIRECT`는 보통 null) |

예시:

```json
{ "type": "GROUP", "memberIds": ["0198f2a2-...", "0198f2a3-..."], "name": "여행 기록방" }
```

#### 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": { "id": "0198f2e0-...", "type": "GROUP", "name": "여행 기록방", "thumbnailKey": null, "memberCount": 3, "createdAt": "2026-07-14T09:30:00Z" },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | `type` 잘못됨, `memberIds` 비어있음 |
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `MEMBER_001` | 초대 대상 사용자 없음 |

### 10-3. 내 채팅방 목록

```http
GET /api/chat/rooms?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

로그인 사용자가 속한(`chat_room_members`) 채팅방 목록을 최근 활동순으로 조회한다. 안 읽은 메시지 수는 `last_read_at` 기준으로 계산한다(구현 시 확정).

#### 인증

필요

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "items": [
      { "id": "0198f2e0-...", "type": "GROUP", "name": "여행 기록방", "thumbnailKey": null, "lastMessagePreview": "안녕하세요", "unreadCount": 2, "updatedAt": "2026-07-14T09:40:00Z" }
    ],
    "nextCursor": null,
    "hasNext": false
  },
  "error": null
}
```

### 10-4. 메시지 히스토리 조회

```http
GET /api/chat/rooms/{roomId}/messages?cursor={uuid}&size=30
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

방의 지난 메시지를 최신→과거 순으로 조회한다. 실시간 수신(10-1)과 별개로 화면 진입 시 히스토리를 채우는 용도다. 방 멤버만 접근 가능하다.

#### 인증

필요 (방 멤버만)

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "items": [
      { "id": "0198f2f0-...", "roomId": "0198f2e0-...", "sender": { "id": "0198f2a1-...", "displayName": "Daily User" }, "content": [ { "type": "text", "value": "안녕하세요" } ], "sentAt": "2026-07-14T09:40:00Z" }
    ],
    "nextCursor": "0198f2ef-...",
    "hasNext": true
  },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `CHAT_002` | 방 멤버 아님 |
| 404 | `CHAT_001` | 존재하지 않는 방 |

### 10-5. 읽음 처리

```http
POST /api/chat/rooms/{roomId}/read
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

호출 시점으로 `chat_room_members.last_read_at`을 갱신한다. 그룹 "읽음 N"은 멤버별 `last_read_at` 비교로 계산한다(ERD 결정).

#### 인증

필요 (방 멤버만)

#### 응답

Status: `200 OK`

```json
{ "success": true, "data": { "roomId": "0198f2e0-...", "lastReadAt": "2026-07-14T09:41:00Z" }, "error": null }
```

---

## 11. 알림 API

```http
GET   /api/notifications?cursor={uuid}&size=20
PATCH /api/notifications/{notificationId}/read
PATCH /api/notifications/read-all
Authorization: Bearer {accessToken}
```

#### 상태

**조회·읽음 처리는 구현됨** (#168, 2026-08-19). 그러나 아래 "알림이 생성되지 않는다"를 반드시 함께 볼 것.

#### 🔴 알림을 만드는 곳이 없다

`NotificationService.save(...)`는 완성돼 있지만 **`notifications` 패키지 밖에서 호출하는 코드가 하나도 없다.**
팔로우 요청·수락·댓글 어느 흐름에서도 알림을 적재하지 않는다.
따라서 `GET /api/notifications`는 **항상 빈 배열**을 반환한다 — API는 살아 있지만 데이터가 생길 경로가 없다.

Sprint 3 게이트 "알림 히스토리 조회"를 실제 데모로 확인하려면 발생 지점 연결이 선행돼야 한다.
FCM 발송(Sprint 4)과 별개로, **저장 트리거는 알림 도메인 쪽 작업**이다.

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
| `type` | `FOLLOW_REQUEST` · `FOLLOW_ACCEPTED` · `COMMENT` · `LIKE` |
| `actor*` | 알림을 발생시킨 사람. 시스템 알림이면 셋 다 `null` |
| `referenceId` | 이동 대상 id(팔로우 행·게시물·댓글 등). **타입별 의미가 다르고 문서화되지 않았다** → §14 |
| `read` | 읽음 여부 |

> `LIKE`는 폐기된 게시물 좋아요(§7)에서 온 값이라 실제로 쓰이지 않는다. 정리 대상(#148).

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
| `FOLLOW_001` | 404 | 존재하지 않는 사용자/팔로우 관계 |
| `FOLLOW_002` | 400 | 자기 자신 팔로우 |
| `FOLLOW_003` | 409 | 이미 존재하는 팔로우 관계 |
| `FOLLOW_004` | 403 | 내게 온 요청이 아님 |
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

> `FOLLOW_001`이 "존재하지 않는 사용자"와 "존재하지 않는 팔로우 관계" 두 상황에 함께 쓰인다.
> FE가 두 경우를 구분해야 한다면 코드를 나눠야 한다 → §14

## 13. 관련 문서

- `docs/api-spec.md`: 인증(JWT)·미디어 Presigned Upload·업로드 커밋 API. 이 문서와 함께 전체 API 명세를 구성한다.
- `docs/erd.md`: 테이블 스키마·설계 결정. 요청/응답 필드 근거.
- `docs/domain-interface-draft.md`: 도메인 간 경계(참조 방식·유저 엔티티 단일화 등). 미확정 항목이 API 스키마에 영향.
- `docs/storage-quota-policy.md`: 구현된 Storage Quota 정책.
- `docs/n+1-audit.md`: 목록/피드 API의 쿼리 수·인덱스 실측 기록. 페이지네이션 설계 근거(§6).
- `docs/sprint2-wrapup.md`: 반응 모델을 댓글 이모지로 단일화한 결정(§6) 등 스프린트 결산.
- `docs/db-migration-guide.md`: Flyway 마이그레이션 규칙. **적용된 마이그레이션 파일은 수정하지 않는다.**

## 14. 열린 결정 사항

2026-08-20(Sprint 3 W8) 기준으로 남은 것만 적는다. 해결된 항목은 본문에 반영했다.

| # | 항목 | 왜 지금 정해야 하나 |
|---|---|---|
| 1 | **공통 응답 봉투 통일 여부**(§2-6) | 8개 엔드포인트가 DTO를 직접 반환한다. 통일은 FE 파싱을 전부 바꾸는 파괴적 변경이라 스프린트 경계에서만 가능하다 |
| 2 | **`profileImage` 키 → URL 변환 주체**(§5-2) | 서버가 presigned URL로 바꿔 줄지, FE가 미디어 API를 한 번 더 부를지. #165와 직결 |
| 3 | **`GET /api/follows/requests` 페이지네이션**(§9-7) | 팔로워/팔로잉 목록은 커서 페이징으로 전환했는데 이 API만 전체를 반환한다 |
| 4 | **알림 저장 트리거 위치**(§11) | 팔로우/댓글 서비스가 직접 호출할지, 이벤트로 뺄지. 정하지 않으면 알림은 계속 빈 배열이다 |
| 5 | **`referenceId`의 타입별 의미**(§11) | FE가 알림 탭 시 어디로 보낼지 판단하려면 타입별 규약이 필요하다 |
| 6 | **댓글 스레드 페이지네이션**(§8-2) | 목록 API 중 유일하게 전체를 반환한다. 댓글이 많은 게시물에서 응답 크기가 제한 없이 커진다 |
| 7 | **`content` JSONB 태그 키 표준** | Sprint 3 "태그/메타데이터 탐색 API"의 선행 조건. `@ValidJson`은 "유효한 JSON"만 보고 구조는 보지 않는다. Sprint 2에서 죽은 GIN 인덱스를 제거했으므로 재도입 여부도 함께 결정 |
| 8 | **`FOLLOW_001` 의미 분리**(§12) | 사용자 없음 · 관계 없음 · PENDING 아님 세 가지에 같은 코드가 쓰인다 |
| 9 | **비로그인 열람 허용 여부**(§6-2) | 서비스는 비로그인 공개글 조회를 지원하는데 보안 설정이 전부 막고 있다. 열 것인지 정해야 문서와 구현이 일치한다 |
| 10 | **채팅 REST/STOMP 경계**(§10) | PR #169가 `WebSocketConfig`를 함께 들고 온다. Sprint 4 착수 전에 범위를 나눠야 한다 |

이전 리비전의 "유저 엔티티 단일화 / 도메인 참조 방식 / 엔티티 필드 네이밍"은 구현이 이미 한 방향으로 굳었다.
남은 것은 문서 정합성 정리(#41)이며, 이 표에서는 제외했다.
