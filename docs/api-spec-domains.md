# memorIN API 명세서 — 도메인 API (유저 / 게시물 / 좋아요 / 댓글 / 팔로우 / 채팅)

> 최신 기준 문서: 2026-07-14
>
> 이 문서는 `docs/api-spec.md`(인증 · 미디어)의 **후속 도메인 명세**다. 노션 전체 API 명세 페이지에서는 미디어 API 다음, 환경 변수 앞에 이어 붙인다.
>
> Notion API 명세서에 남아 있는 이전 주제/초안 내용은 잔재일 수 있다. 최신 명세는 이 레포의 `docs/` 문서를 기준으로 확인한다.

## 0. 이 문서의 구현 상태

아래 도메인 API는 **엔티티/ERD는 있으나 컨트롤러·서비스는 아직 구현 전(설계 초안)** 이다. 요청/응답 스키마는 현재 엔티티 필드와 `docs/erd.md`를 근거로 작성한 **초안**이며, 실제 구현 PR에서 확정한다.

| 도메인 | 엔티티 | 컨트롤러/API | 이 문서 상태 |
|---|---|---|---|
| 유저 / 프로필 | `User` 구현됨 | 없음 | 설계 초안 |
| 게시물 | `Post`, `PostMedia` 구현됨 | 없음 | 설계 초안 |
| 좋아요 | `PostLikes` 구현됨 | 없음 | 설계 초안 |
| 댓글 | `PostComments` 구현됨 | 없음 | 설계 초안 |
| 팔로우 | `Follows` 구현됨 | 없음 | 설계 초안 |
| 채팅 | `ChatRooms`, `ChatRoomMembers`, `Messages` 구현됨 | STOMP 에코 테스트만 구현 | 실시간 일부 구현 · REST 설계 초안 |

> 공통 규칙(Base URL, Content-Type, 인증 헤더, 공통 응답 봉투 `{success, data, error}`)은 `docs/api-spec.md` §2를 따른다. 이 문서는 그 위에 **페이지네이션 규칙(§2-5)만 보강**한다.

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

---

## 5. 유저 / 프로필 API

### 5-1. 내 프로필 조회

```http
GET /api/users/me
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안. `User` 엔티티는 구현됨, 조회 API 미구현.

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

설계 초안.

#### 설명

다른 사용자의 공개 프로필을 조회한다. 민감 필드(`email`)는 제외한다. 팔로워 수·팔로잉 수 등 집계는 구현 시 포함 여부를 확정한다.

#### 인증

필요

#### 경로 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `userId` | string(uuid) | 조회 대상 사용자 id |

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "id": "0198f2a1-8b3c-7def-9012-3456789abcde",
    "username": "daily_user",
    "displayName": "Daily User",
    "bio": "매일 기록합니다",
    "profileImageKey": "uploads/2026/07/01/{uuid}/profile.jpg"
  },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `MEMBER_001` | 존재하지 않는 사용자 |

### 5-3. 내 프로필 수정

```http
PATCH /api/users/me
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

설계 초안.

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

설계 초안. `Post`/`PostMedia` 엔티티 구현됨, API 미구현.

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

설계 초안.

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

설계 초안.

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
| `scope` | string(enum) | X | `ALL`\|`FOLLOWING`, 기본 `ALL` |

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
| 400 | `COMMON_002` | `size` 범위 초과, `cursor` 형식 오류 |
| 401 | `AUTH_001` | 인증 누락/만료 |

### 6-4. 게시물 수정

```http
PATCH /api/posts/{postId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

설계 초안.

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

설계 초안.

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

---

## 7. 좋아요 API

`post_likes`는 `(post_id, user_id)` 유니크 제약이 있어 **1인 1좋아요**다.

### 7-1. 좋아요 등록

```http
POST /api/posts/{postId}/likes
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안. `PostLikes` 엔티티 구현됨, API 미구현.

#### 설명

로그인 사용자가 게시물에 좋아요를 등록한다. 이미 좋아요한 상태면 멱등 처리한다(중복 생성 금지).

#### 인증

필요

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": { "postId": "0198f2b0-...", "liked": true, "likeCount": 4 },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `POST_001` | 없거나 삭제된 게시물 |

### 7-2. 좋아요 취소

```http
DELETE /api/posts/{postId}/likes
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

좋아요를 취소한다. 좋아요하지 않은 상태여도 멱등 처리한다.

#### 인증

필요

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": { "postId": "0198f2b0-...", "liked": false, "likeCount": 3 },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `POST_001` | 없거나 삭제된 게시물 |

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

설계 초안. `PostComments` 엔티티 구현됨, API 미구현.

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
    "id": "0198f2c0-...",
    "postId": "0198f2b0-...",
    "parentId": null,
    "author": { "id": "0198f2a1-...", "username": "daily_user", "displayName": "Daily User", "profileImageKey": "uploads/.../profile.jpg" },
    "body": "좋은 기록이네요!",
    "createdAt": "2026-07-14T09:10:00Z"
  },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | `COMMON_002` | `body` 누락/공백 |
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `POST_001` | 없거나 삭제된 게시물 |
| 404 | `COMMENT_001` | `parentId`가 존재하지 않는 댓글 |

### 8-2. 댓글 목록 조회

```http
GET /api/posts/{postId}/comments?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

게시물의 댓글을 조회한다. 대댓글 표현 방식(평면 목록 + `parentId` vs 트리 중첩)은 구현 시 확정한다. 페이지네이션은 §2-5를 따른다.

#### 인증

필요

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "items": [
      { "id": "0198f2c0-...", "parentId": null, "author": { }, "body": "좋은 기록이네요!", "createdAt": "2026-07-14T09:10:00Z" }
    ],
    "nextCursor": "0198f2bf-...",
    "hasNext": false
  },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 404 | `POST_001` | 없거나 삭제된 게시물 |

### 8-3. 댓글 삭제

```http
DELETE /api/comments/{commentId}
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

본인 댓글을 삭제한다. `deleted_at`을 채우는 소프트 삭제다. 대댓글이 달린 부모 댓글 삭제 시 표시 정책("삭제된 댓글입니다" 등)은 구현 시 확정한다.

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

설계 초안. `Follows` 엔티티 구현됨, API 미구현.

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

### 9-2. 언팔로우 / 요청 취소

```http
DELETE /api/follows/{followingId}
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

대상에 대한 내 팔로우(또는 대기 중 요청)를 해제한다. 없는 관계여도 멱등 처리한다.

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

### 9-3. 팔로우 요청 수락

```http
POST /api/follows/{followId}/accept
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

나에게 온 `PENDING` 팔로우 요청을 수락해 `ACCEPTED`로 바꾼다. 요청 대상(`following_id`)이 본인일 때만 가능하다. 거절은 `DELETE`(9-2 계열) 또는 별도 `/reject` 엔드포인트로 처리한다(정책 미정).

#### 인증

필요 (요청의 `following_id` 본인만)

#### 응답

Status: `200 OK`

```json
{
  "success": true,
  "data": { "id": "0198f2d0-...", "followerId": "0198f2a3-...", "status": "ACCEPTED" },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 401 | `AUTH_001` | 인증 누락/만료 |
| 403 | `FOLLOW_004` | 내게 온 요청이 아님 |
| 404 | `FOLLOW_001` | 존재하지 않는 팔로우 요청 |

### 9-4. 팔로워 목록 조회

```http
GET /api/users/{userId}/followers?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

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

### 9-5. 팔로잉 목록 조회

```http
GET /api/users/{userId}/followings?cursor={uuid}&size=20
Authorization: Bearer {accessToken}
```

#### 상태

설계 초안.

#### 설명

해당 유저가 팔로우하는(=`follower_id = userId`, `status = ACCEPTED`) 사용자 목록을 조회한다. 응답 스키마는 9-4와 동일하다.

#### 인증

필요

---

## 10. 채팅 API (STOMP + REST)

채팅은 **실시간 전송은 WebSocket/STOMP**, **방·멤버·메시지 관리는 REST**로 나눈다.

- 현재 구현: `ChatController`의 STOMP **에코 테스트**만 동작(방/메시지 영속화 없음). `docs/api-spec.md` §6 체크리스트대로 DB 스키마 확정 후 실제 채팅 도메인을 설계한다.
- REST(방 생성/목록/메시지 조회)는 **설계 초안**이다.
- 인증: 현재 STOMP 경로는 `SecurityConfig`에서 `/ws/**`, `/*.html`이 임시 `permitAll`이다. WebSocket 토큰 인증은 Sprint 1에서 별도 설계한다(`docs/auth-jwt-design.md` §6).

### 10-1. 실시간 메시지 (STOMP) — 구현됨(에코 테스트)

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

## 11. 추가 예정 에러코드

아래 코드는 이 문서의 도메인 API 구현 시 `global/common/ErrorCode.java`에 추가해야 한다(현재는 `COMMON_*`, `AUTH_*`, `MEMBER_*`, `MEDIA_001`만 존재). 코드 컨벤션은 기존과 동일하게 `도메인_번호`, HTTP 상태를 함께 정의한다.

| 코드(안) | HTTP | 상황 |
|---|---:|---|
| `POST_001` | 404 | 존재하지 않거나 삭제된 게시물 |
| `POST_002` | 403 | 게시물 접근/수정 권한 없음 |
| `COMMENT_001` | 404 | 존재하지 않거나 삭제된 댓글 |
| `COMMENT_002` | 403 | 댓글 작성자 아님 |
| `FOLLOW_001` | 404 | 존재하지 않는 팔로우 요청 |
| `FOLLOW_002` | 400 | 자기 자신 팔로우 시도 |
| `FOLLOW_003` | 409 | 이미 존재하는 팔로우 관계 |
| `FOLLOW_004` | 403 | 내게 온 요청이 아님 |
| `CHAT_001` | 404 | 존재하지 않는 채팅방 |
| `CHAT_002` | 403 | 채팅방 멤버 아님 |

## 12. 관련 문서

- `docs/api-spec.md`: 인증(JWT)·미디어 Presigned Upload API. 이 문서와 함께 전체 API 명세를 구성한다.
- `docs/erd.md`: 테이블 스키마·설계 결정. 요청/응답 필드 근거.
- `docs/domain-interface-draft.md`: 도메인 간 경계(참조 방식·유저 엔티티 단일화 등). 미확정 항목이 API 스키마에 영향.
- `docs/storage-quota-design.md`: 업로드 confirm·quota 설계.

## 13. 명세 확정 전 열린 결정 사항

이 문서는 초안이므로 아래가 정해지면 갱신한다.

- **유저 엔티티 단일화** — `member.entity.Member` vs `domain.users.User` 중복. 응답의 작성자 요약 필드가 여기 의존(`docs/domain-interface-draft.md` §3-A).
- **도메인 참조 방식** — 엔티티 직접 참조 vs UUID만. 목록 API의 N+1·요약 조회 방식에 영향(§3-B).
- **엔티티 필드 네이밍** — 일부 엔티티가 스네이크케이스 Java 필드(`post_id`, `created_at` 등) 사용 중. **API JSON은 카멜케이스로 통일**하되 내부 정리는 §3-C 컨벤션 확정 후.
- **집계 필드(likeCount/commentCount/unreadCount)** 포함 위치와 N+1 대응.
- **content JSONB 블록 스키마**(type 종류·필드) 표준.
