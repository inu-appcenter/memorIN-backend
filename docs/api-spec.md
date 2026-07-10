# memorIN API 명세서

> 최신 기준 문서: 2026-07-10
>
> Notion API 명세서에 남아 있는 이전 주제/초안 내용은 잔재일 수 있다. 최신 명세는 이 레포의 `docs/` 문서를 기준으로 확인한다.

## 1. 문서 범위

이 문서는 `docs/auth-jwt-design.md`와 `docs/presigned-upload-api.md`를 API 명세서 형식으로 통합한 문서다.

| 구분 | 상태 | 비고 |
|---|---|---|
| 인증/회원가입 API | 일부 구현, JWT 발급은 예정 | `POST /auth/signup`, `POST /auth/login` |
| JWT 재발급/로그아웃 API | 설계 예정 | JWT 구현 PR에서 추가 |
| 미디어 Presigned Upload API | 구현됨 | 현재 JWT 필터 도입 전까지 임시 permitAll |

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

현재 `SecurityConfig`는 `/api/media/**`를 임시로 허용하고 있다. JWT 필터 도입 시 `POST /api/media/presigned-upload-url`은 인증 필수 API로 전환한다.

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

이메일과 비밀번호를 검증하고 Access Token을 반환한다.

현재 코드는 로그인 성공 시 임시 문자열 `"로그인 성공"`을 `accessToken` 필드에 반환한다. JWT 구현 이후 실제 Access Token으로 교체한다.

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

Status: `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  },
  "error": null
}
```

현재 임시 응답:

```json
{
  "success": true,
  "data": {
    "accessToken": "로그인 성공"
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

### 3-4. 토큰 재발급 예정

```http
POST /auth/reissue
Content-Type: application/json
```

#### 상태

아직 구현 전이다.

#### 요청 Body 초안

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### 응답 Body 초안

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

#### 처리 규칙 초안

1. Refresh Token 서명과 만료를 검증한다.
2. 서버 저장소의 토큰과 요청 토큰이 일치하는지 확인한다.
3. Access Token과 Refresh Token을 모두 새로 발급한다.
4. 저장소의 Refresh Token 값을 새 토큰으로 갱신한다.

Refresh Token 저장소는 Redis를 우선 권장한다.

```text
refresh:{userId} -> refreshToken
TTL: 7 days
```

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

JWT 적용 후 필수.

현재 구현은 `SecurityConfig`에서 `/api/media/**`가 임시 `permitAll`로 열려 있다. JWT 필터 도입 시 인증 필수로 변경한다.

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
| 401 | 공통 인증 오류 | JWT 적용 후 인증 누락/만료/위조 |
| 500 | `{ "message": "Failed to create presigned upload URL." }` | MinIO URL 발급 실패 |

### 4-2. 업로드 완료 Confirm API 예정

```http
POST /api/media/uploads/confirm
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 상태

아직 구현 전이다.

#### 필요성

Presigned URL 발급만으로는 실제 업로드 성공 여부, 저장된 객체 크기, 사용자 quota 사용량, 게시물과의 연결 상태를 확정할 수 없다. 클라이언트 업로드 성공 후 백엔드에 confirm 요청을 보내고, 백엔드는 MinIO object stat과 DB 상태를 검증해야 한다.

#### 요청 Body 초안

```json
{
  "objectKey": "uploads/2026/07/01/{uuid}/daily-photo.jpg",
  "contentType": "image/jpeg",
  "contentLength": 1048576,
  "purpose": "POST_MEDIA"
}
```

#### 처리 규칙 초안

1. 인증 사용자와 `objectKey` 소유자를 확인한다.
2. MinIO에서 객체 존재 여부와 실제 크기를 확인한다.
3. 실제 MIME 타입 검증 또는 비동기 스캐닝 정책을 적용한다.
4. 사용자 quota를 최종 검증한다.
5. 미디어 메타데이터를 DB에 저장하거나 PENDING 상태를 COMPLETE로 변경한다.

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

### 5-3. Storage Quota 예정

| 이름 | 기본값 | 설명 |
|---|---:|---|
| `STORAGE_QUOTA_DEFAULT_LIMIT_BYTES` | `1073741824` | 사용자 기본 저장 용량 제한, 1GiB |
| `STORAGE_QUOTA_MAX_SINGLE_UPLOAD_BYTES` | `52428800` | 단일 업로드 최대 크기, 50MiB |
| `STORAGE_QUOTA_PENDING_TTL_SECONDS` | `900` | PENDING 업로드 메타데이터 만료 시간, 15분 |

## 6. 구현 체크리스트

### 6-1. 인증/JWT

- `POST /auth/signup` 구현 완료.
- `POST /auth/login` 비밀번호 검증 구현 완료.
- 로그인 성공 응답의 임시 문자열을 실제 Access Token으로 교체한다.
- Refresh Token 발급과 Redis 저장을 추가한다.
- `JwtTokenProvider`를 추가해 발급/검증을 담당하게 한다.
- `JwtAuthenticationFilter`를 추가해 `Authorization: Bearer` 토큰을 검증한다.
- `/api/media/**` 임시 `permitAll`을 제거하고 필요한 경로만 인증 예외로 둔다.
- `POST /auth/reissue`, `POST /auth/logout`을 추가한다.

### 6-2. 미디어 업로드

- `POST /api/media/presigned-upload-url` 구현 완료.
- JWT 적용 후 인증 사용자 기반 object key 정책으로 변경한다.
- 업로드 완료 confirm API를 추가한다.
- confirm 단계에서 MinIO object stat 기반 실제 크기 검증을 수행한다.
- 사용자별 quota 정책을 적용한다.
- 운영 전 MIME 타입 신뢰 경계를 정리하고 검증/스캐닝 정책을 결정한다.

## 7. 관련 문서

- `docs/auth-jwt-design.md`: JWT 설계 초안. 최신 API 명세는 이 문서를 우선한다.
- `docs/presigned-upload-api.md`: Presigned Upload 초안. 최신 API 명세는 이 문서를 우선한다.
- `docs/storage-quota-design.md`: 업로드 confirm 및 quota 설계 참고.
- `docs/minio-bucket-policy.md`: MinIO private bucket 운영 정책 참고.
