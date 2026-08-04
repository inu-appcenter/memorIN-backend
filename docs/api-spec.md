# memorIN API 명세서

> 최신 기준 문서: 2026-08-04
>
> Notion API 명세서에 남아 있는 이전 주제/초안 내용은 잔재일 수 있다. 최신 명세는 이 레포의 `docs/` 문서를 기준으로 확인한다.

> **전체 엔드포인트의 정본(live)은 Swagger UI다.** 앱 실행 후 `http://localhost:8080/swagger-ui/index.html`에서
> 그룹(인증·게시물·댓글·팔로우·사용자·미디어·FCM 토큰)별 최신 목록과 요청/응답 스키마를 확인한다.
> 이 문서는 그 위에 **공통 규칙·인증 정책·에러코드**처럼 Swagger가 자동 생성하지 못하는 맥락을 보충한다.

## 1. 문서 범위

이 문서는 `docs/auth-jwt-design.md`와 (삭제된) `docs/presigned-upload-api.md` 초안을 API 명세서 형식으로 통합한 문서다.

| 구분 | 상태 | 비고 |
|---|---|---|
| 인증/회원가입 API | **구현됨** | `POST /auth/signup`, `POST /auth/login` (JWT 발급) |
| JWT 재발급 API | **구현됨** | `POST /auth/refresh` (Access/Refresh 재발급) |
| 로그아웃 API | 설계 예정 | 저장소 Refresh Token 삭제 방식 검토 |
| 미디어 Presigned Upload / 업로드 커밋 / Storage Quota | 구현됨 | JWT 인증 필수, `/api/media/**` permitAll 제외됨 |
| 게시물·댓글·팔로우·사용자 API | 구현됨 | 도메인 상세는 `docs/api-spec-domains.md` + Swagger UI 참고 |

## 2. 공통 규칙

### 2-1. Base URL

| 환경 | Base URL |
|---|---|
| 로컬 Docker Compose | `http://localhost:8080` |

### 2-2. Content-Type

요청/응답 본문은 기본적으로 JSON을 사용한다.

```http
Content-Type: application/json
```

### 2-3. 인증 헤더

JWT 적용 후 인증이 필요한 API는 아래 헤더를 사용한다.

```http
Authorization: Bearer {accessToken}
```

`/api/media/**`는 JWT 필터 도입에 맞춰 `permitAll`에서 제외됐다. `POST /api/media/presigned-upload-url`을 포함한 미디어 API는 인증 필수다.

### 2-4. 공통 응답 포맷

`AuthController` 계열 API는 `ApiResponse<T>` 공통 응답 봉투를 사용한다.

성공:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_002",
    "message": "..."
  }
}
```

미디어 presigned API는 현재 `PresignedUploadResponse`를 직접 반환하고, 일부 예외는 `{ "message": "..." }` 형태로 반환한다. 공통 응답 포맷 적용 여부는 추후 API 정리 시 결정한다.

## 3. 인증 API

### 3-1. 회원가입

```http
POST /auth/signup
Content-Type: application/json
```

#### 설명

이메일, 비밀번호, 사용자명, 표시명을 받아 사용자를 생성한다. 비밀번호는 BCrypt로 해시해 저장한다.

#### 인증

불필요

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `email` | string | O | `@Email`, blank 불가 | 로그인 식별자로 사용하는 이메일 |
| `password` | string | O | 8~64자, blank 불가 | 비밀번호 원문. 서버 저장 시 해시 처리 |
| `username` | string | O | 최대 50자, blank 불가 | 서비스 내 고유 사용자명 |
| `displayName` | string | O | 최대 50자, blank 불가 | 화면 표시명 |

예시:

```json
{
  "email": "user@example.com",
  "password": "password123",
  "username": "daily_user",
  "displayName": "Daily User"
}
```

#### 응답

Status: `201 Created`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | validation error | 요청 필드 형식/길이 검증 실패 |
| 409 또는 공통 예외 정책에 따름 | `MEMBER_002` | 이미 사용 중인 이메일 |
| 409 또는 공통 예외 정책에 따름 | `MEMBER_003` | 이미 사용 중인 username |

### 3-2. 로그인

```http
POST /auth/login
Content-Type: application/json
```

#### 설명

이메일과 비밀번호를 검증하고 **Access Token과 Refresh Token을 함께 발급**한다. 두 토큰 모두 HS256으로 서명한 실제 JWT다.

#### 인증

불필요

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `email` | string | O | blank 불가 | 회원가입한 이메일 |
| `password` | string | O | blank 불가 | 비밀번호 |

예시:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

#### 응답

Status: `200 OK` — 공통 `ApiResponse` 봉투로 감싸 반환한다.

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  },
  "error": null
}
```

#### 주요 실패 케이스

| HTTP Status | 코드 | 상황 |
|---:|---|---|
| 400 | validation error | 요청 필드 검증 실패 |
| 401 | `AUTH_002` | 이메일 또는 비밀번호 불일치 |

### 3-3. Access Token 적용 규칙

JWT 구현 후 Access Token은 인증이 필요한 API 요청마다 `Authorization` 헤더에 포함한다.

```http
Authorization: Bearer {accessToken}
```

권장 토큰 정책:

| 항목 | Access Token | Refresh Token |
|---|---|---|
| 만료 | 15분 (`JWT_ACCESS_EXPIRY_MS=900000`) | 7일 (`JWT_REFRESH_EXPIRY_MS=604800000`) |
| 용도 | API 요청 인증 | Access Token 재발급 |
| 서버 저장 | 저장하지 않음 | Redis 저장 권장 |
| 서명 | HS256 | HS256 |

Access Token 클레임은 `sub`(user/member id), `iat`, `exp`만 우선 사용한다. 권한 정보는 도메인 요구가 생기면 추가한다.

### 3-4. 토큰 재발급

```http
POST /auth/refresh
Content-Type: application/json
```

#### 상태

**구현됨.** (경로는 `/auth/reissue`가 아니라 `/auth/refresh`다.)

#### 인증

불필요. Refresh Token 자체를 Body로 전달한다. (SecurityConfig에서 이 경로는 permitAll)

#### 요청 Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refreshToken` | string | O | 로그인 시 발급받은 Refresh Token |

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### 응답 Body

Status: `200 OK`

> ⚠️ 현재 이 API는 로그인과 달리 **공통 `ApiResponse` 봉투 없이** `LoginResponse`(토큰 쌍)를 그대로 반환한다.
> 응답 포맷 일관성은 후속 API 정리 시 맞춘다.

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### 처리 규칙

1. Refresh Token 서명과 만료를 검증한다.
2. Access Token과 Refresh Token을 새로 발급해 반환한다.

### 3-5. 로그아웃 예정

```http
POST /auth/logout
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

아직 구현 전이다.

#### 처리 규칙 초안

서버 저장소에서 `refresh:{userId}`를 삭제한다. Access Token은 짧은 만료 시간을 전제로 자연 만료를 허용한다. 즉시 차단이 필요해지면 Access Token blacklist를 별도 검토한다.

## 4. 미디어 Presigned Upload API

### 4-1. 업로드 URL 발급

```http
POST /api/media/presigned-upload-url
Content-Type: application/json
Authorization: Bearer {accessToken}
```

#### 설명

클라이언트가 MinIO에 직접 업로드할 수 있도록 presigned PUT URL을 발급한다. 요청 시 설정된 버킷이 없으면 백엔드가 자동 생성한다.

현재 기능 명세 확정 전까지 파일 메타데이터는 DB에 저장하지 않고, URL 발급만 수행한다.

#### 인증

필수. `/api/media/**`는 `permitAll` 예외 없이 JWT 인증을 거친다.

#### 요청 Body

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---:|---|---|
| `fileName` | string | O | blank 불가 | 원본 파일명 |
| `contentType` | string | O | blank 불가, 허용 MIME 타입 정책 적용 | 업로드할 파일 MIME 타입 |
| `contentLength` | number | O | 1 이상, 최대 업로드 크기 이하 | 업로드 예정 파일 크기(bytes) |

예시:

```json
{
  "fileName": "daily-photo.jpg",
  "contentType": "image/jpeg",
  "contentLength": 1048576
}
```

#### 응답 Body

Status: `200 OK`

```json
{
  "uploadUrl": "http://localhost:9000/memorin-media/uploads/...",
  "objectKey": "uploads/2026/07/01/{uuid}/daily-photo.jpg",
  "method": "PUT",
  "requiredHeaders": {
    "Content-Type": "image/jpeg"
  },
  "expiresAt": "2026-07-01T12:10:00Z",
  "maxUploadSizeBytes": 52428800
}
```

#### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `uploadUrl` | string | 클라이언트가 직접 PUT 요청을 보낼 presigned URL |
| `objectKey` | string | MinIO bucket 내부 객체 키 |
| `method` | string | 업로드 HTTP method. 현재 `PUT` |
| `requiredHeaders` | object | 업로드 요청에 그대로 포함해야 하는 헤더 |
| `expiresAt` | string | presigned URL 만료 시각 |
| `maxUploadSizeBytes` | number | 서버가 허용하는 단일 업로드 최대 크기 |

#### 클라이언트 업로드 요청

백엔드 응답의 `uploadUrl`로 직접 PUT 요청을 보낸다. `requiredHeaders` 값은 변경하지 않고 포함해야 한다.

```http
PUT {uploadUrl}
Content-Type: image/jpeg

<binary>
```

#### 주요 실패 케이스

| HTTP Status | 응답 | 상황 |
|---:|---|---|
| 400 | `{ "message": "Invalid presigned upload request." }` | 필수 필드 누락 또는 `contentLength < 1` |
| 400 | `{ "message": "..." }` | 허용되지 않는 MIME 타입, 최대 크기 초과 등 요청 정책 위반 |
| 401 | 공통 인증 오류 | 인증 누락/만료/위조 |
| 500 | `{ "message": "Failed to create presigned upload URL." }` | MinIO URL 발급 실패 |

### 4-2. 업로드 완료 커밋

별도의 `/api/media/uploads/confirm` API는 만들지 않았다. 대신 게시물 생성/수정 API(`POST /api/posts` 등, `docs/api-spec-domains.md` 참고)가 `attachments`로 전달된 `fileKey` 목록을 받아 첨부 시점에 커밋을 수행한다(`PostService.saveMedia` → `MediaUploadCommitService.commitUpload`).

Presigned URL 발급만으로는 실제 업로드 성공 여부, 저장된 객체 크기, 사용자 quota 사용량을 확정할 수 없으므로, 커밋 시 다음을 검증한다.

1. pending 예약(`pending_uploads`)의 소유자·만료 여부를 확인한다.
2. MinIO `statObject`로 실제 업로드 크기(`actualBytes`)를 확인한다. 클라이언트가 선언한 `contentLength`는 신뢰하지 않는다.
3. `actualBytes`가 단일 파일 상한(`MINIO_MAX_UPLOAD_SIZE_BYTES`) 이하인지 확인한다.
4. `actualBytes` 기준으로 사용자 전체 quota(`STORAGE_QUOTA_DEFAULT_LIMIT_BYTES`)를 재검증한다(`StorageQuotaService.assertCommitWithinLimit`).
5. `pending_uploads` 행을 삭제하고, `actualBytes`를 `post_media.file_size_bytes`에 기록한다.

세부 동작·동시성 보장·알려진 한계는 `docs/storage-quota-policy.md`를 기준으로 한다.

### 4-3. 압축 가이드 정책

서버는 presigned PUT으로 업로드되는 파일 바이트를 직접 만지지 않으므로 서버 사이드 압축은 하지 않는다.
대신 클라이언트(앱/웹)가 업로드 전 이미지를 압축할 때 참고할 가이드 값을 아래 API로 내려준다. **서버가 강제하는 값이 아니며**, 클라이언트가 이 값보다 큰 파일을 올려도 `MINIO_MAX_UPLOAD_SIZE_BYTES`/Quota 검증만 통과하면 업로드는 성공한다.

```http
GET /api/media/compression-policy
```

응답:

```json
{
  "imageQualityPercent": 80,
  "imageMaxWidthPx": 1920,
  "imageMaxHeightPx": 1920
}
```

| 이름 | 기본값 | 설명 |
|---|---:|---|
| `MEDIA_COMPRESSION_IMAGE_QUALITY_PERCENT` | `80` | 이미지 압축 품질(1~100) |
| `MEDIA_COMPRESSION_IMAGE_MAX_WIDTH_PX` | `1920` | 이미지 최대 가로 픽셀 |
| `MEDIA_COMPRESSION_IMAGE_MAX_HEIGHT_PX` | `1920` | 이미지 최대 세로 픽셀 |

동영상(`video/mp4`, `video/quicktime`)은 현재 압축 가이드 대상이 아니다.

### 4-4. 스토리지 사용량 조회 (대시보드)

인증된 사용자 본인의 스토리지 사용 현황을 조회한다. `usedBytes`는 커밋된 미디어(`post_media`)와 만료 전 pending 예약(`pending_uploads`)의 합산값이며, 별도 집계 테이블 없이 요청 시점에 계산한다(`StorageQuotaService.getUsedBytes`, `docs/storage-quota-policy.md` 참고).

```http
GET /api/media/quota
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "usedBytes": 2147483648,
  "totalQuotaBytes": 5368709120,
  "remainingBytes": 3221225472,
  "usagePercent": 40.0,
  "warning": false
}
```

| 필드 | 설명 |
|---|---|
| `usedBytes` | 현재 사용 중인 바이트(커밋 + pending 예약 합산) |
| `totalQuotaBytes` | 전체 할당 용량(`STORAGE_QUOTA_DEFAULT_LIMIT_BYTES`) |
| `remainingBytes` | 남은 용량. 사용량이 한도를 넘어도 음수가 되지 않고 0으로 고정된다 |
| `usagePercent` | 사용률(%). `usedBytes / totalQuotaBytes * 100`. 한도 초과 시 100을 넘을 수 있다 |
| `warning` | 사용률이 경고 임계값(`STORAGE_QUOTA_WARNING_THRESHOLD_PERCENT`, 기본 80%) 이상이면 `true` |

요청 바디는 없으며, 대상 사용자는 request 파라미터가 아니라 JWT(SecurityContext)의 인증 정보로 결정된다(quota 우회 방지). 인증 없이 호출하면 401을 반환한다.

## 5. 환경 변수

### 5-1. JWT

| 이름 | 예시/기본값 | 설명 |
|---|---|---|
| `JWT_SECRET` | 직접 설정 | HS256 서명 시크릿. 256비트 이상 권장, 커밋 금지 |
| `JWT_ACCESS_EXPIRY_MS` | `900000` | Access Token 만료 시간, 15분 |
| `JWT_REFRESH_EXPIRY_MS` | `604800000` | Refresh Token 만료 시간, 7일 |

### 5-2. MinIO Presigned Upload

| 이름 | 기본값 | 설명 |
|---|---|---|
| `MINIO_ENDPOINT` | `http://localhost:9000` | 백엔드가 MinIO에 접근할 내부 주소 |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` | 클라이언트가 직접 업로드할 때 접근할 주소 |
| `MINIO_BUCKET_NAME` | `memorin-media` | 업로드 버킷 |
| `MINIO_PRESIGNED_UPLOAD_EXPIRY_SECONDS` | `600` | 업로드 URL 만료 시간 |
| `MINIO_MAX_UPLOAD_SIZE_BYTES` | `52428800` | 요청 단계의 최대 파일 크기 |
| `MINIO_ALLOWED_CONTENT_TYPES` | 이미지/동영상 일부 | 허용 MIME 타입 |

Docker 내부 백엔드는 `MINIO_ENDPOINT=http://minio:9000`을 사용하지만, 호스트 브라우저나 앱은 보통 `MINIO_PUBLIC_ENDPOINT=http://localhost:9000`으로 접근해야 한다.

### 5-3. Storage Quota

구현됨. 환경 변수 전체 목록과 각 값의 의미는 `docs/storage-quota-policy.md` §환경 변수를 기준으로 한다(`STORAGE_QUOTA_DEFAULT_LIMIT_BYTES`, `STORAGE_QUOTA_PENDING_TTL_SECONDS`, `MINIO_MAX_UPLOAD_SIZE_BYTES`). 이 문서에서는 중복 표기하지 않는다.

## 6. 구현 체크리스트

### 6-1. 인증/JWT

- `POST /auth/signup` 구현 완료.
- `POST /auth/login` 비밀번호 검증 구현 완료.
- 로그인 응답을 실제 Access/Refresh Token 발급으로 교체. **완료**
- `JwtTokenProvider`로 발급/검증 담당. **완료**
- `JwtAuthenticationFilter`로 `Authorization: Bearer` 토큰 검증. **완료**
- `/api/media/**` 임시 `permitAll`을 제거하고 필요한 경로만 인증 예외로 둔다. **완료**
- `POST /auth/refresh` 토큰 재발급 추가. **완료** (§3-4)
- Refresh Token 서버 저장(Redis) — 미적용. 후속 검토.
- `POST /auth/logout` — 미구현. 후속 검토.

### 6-2. 미디어 업로드

- `POST /api/media/presigned-upload-url` 구현 완료.
- JWT 적용 후 인증 사용자 기반 object key 정책으로 변경한다. **완료**
- 업로드 완료 커밋을 게시물 생성 API 경로(`attachments`)로 구현한다. **완료** (§4-2)
- 커밋 단계에서 MinIO object stat 기반 실제 크기 검증을 수행한다. **완료** (§4-2)
- 사용자별 quota 정책을 적용한다(예약 시 선언값 기준 + 커밋 시 실제 크기 기준 재검증). **완료** (`docs/storage-quota-policy.md`)
- 운영 전 MIME 타입 신뢰 경계를 정리하고 검증/스캐닝 정책을 결정한다.

## 7. 관련 문서

- `docs/auth-jwt-design.md`: JWT 설계 초안. 최신 API 명세는 이 문서를 우선한다.
- `docs/storage-quota-policy.md`: 구현된 Storage Quota 정책 (사용량 산정, 동시성 보장, 환경 변수, 알려진 한계).
- `docs/minio-bucket-policy.md`: MinIO private bucket 운영 정책 참고.
