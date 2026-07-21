# Presigned Upload API 초안

> 최신 API 명세는 [`docs/api-spec.md`](api-spec.md)를 기준으로 한다.
> 이 문서는 Presigned Upload 초기 구현 메모를 보존하기 위한 초안이며, Notion API 명세서에 남아 있는 이전 주제 잔재보다 레포 `docs/` 문서를 우선한다.

## 현재 구현 범위

기능 명세 확정 전까지는 파일 메타데이터를 DB에 저장하지 않고, MinIO 업로드용 presigned URL만 발급한다.
요청 시 설정된 버킷이 없으면 백엔드가 자동 생성한다.

```http
POST /api/media/presigned-upload-url
Content-Type: application/json
```

요청:

```json
{
  "fileName": "daily-photo.jpg",
  "contentType": "image/jpeg",
  "contentLength": 1048576
}
```

응답:

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

클라이언트는 응답의 `uploadUrl`로 `PUT` 요청을 보내고, `requiredHeaders`의 값을 그대로 포함해야 한다.

## 환경 변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `MINIO_ENDPOINT` | `http://localhost:9000` | 백엔드가 MinIO에 접근할 내부 주소 |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` | 클라이언트가 직접 업로드할 때 접근할 주소 |
| `MINIO_BUCKET_NAME` | `memorin-media` | 업로드 버킷 |
| `MINIO_PRESIGNED_UPLOAD_EXPIRY_SECONDS` | `600` | 업로드 URL 만료 시간 |
| `MINIO_MAX_UPLOAD_SIZE_BYTES` | `52428800` | 요청 단계의 최대 파일 크기 |
| `MINIO_ALLOWED_CONTENT_TYPES` | 이미지/동영상 일부 | 허용 MIME 타입 |

Docker 내부 백엔드는 `MINIO_ENDPOINT=http://minio:9000`을 사용하지만, 호스트 브라우저나 앱은 보통 `MINIO_PUBLIC_ENDPOINT=http://localhost:9000`으로 접근해야 한다.

## 명세 확정 시 결정할 항목

- 인증 적용: 현재 `SecurityConfig`가 모든 요청을 허용하므로, JWT 완성 후 이 API는 로그인 사용자만 호출 가능해야 한다.
- 객체 키 정책: 최종적으로는 `users/{userId}/...` 또는 `posts/{postId}/...`처럼 소유자/도메인이 드러나는 prefix가 필요하다.
- 업로드 완료 처리: 클라이언트 업로드 성공 후 백엔드에 `objectKey`, 크기, MIME 타입, 용도 등을 등록하는 confirm API가 필요하다.
- Quota: 현재는 요청값 기준으로만 크기를 제한한다. 실제 저장량은 confirm 단계에서 MinIO object stat 또는 DB 누적값으로 검증해야 한다.
- 파일 검증: MIME 타입은 클라이언트 입력값만 믿으면 안 된다. 운영 전에는 백엔드 검증 또는 비동기 스캐닝 정책을 정해야 한다.
- 다운로드: private bucket 유지가 기본이므로 조회도 presigned GET URL 발급 방식으로 분리하는 것이 좋다.
