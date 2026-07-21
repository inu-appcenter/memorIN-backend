# Storage Quota 설계 초안

## 배경

미디어 업로드 기능에 사용자별 저장 공간 제한(Quota)을 적용하기 위해, 구현 전에 사용량 집계 기준과 환경변수 초안을 정리한다.

## Quota 집계 방향

초기 구현에서는 별도 `user_storage_quotas` 집계 테이블을 바로 도입하지 않고, 업로드 완료된 미디어 메타데이터의 `file_size_bytes` 합산으로 사용자별 사용량을 계산하는 방식을 우선 검토한다.

예상 집계 기준:

```sql
SELECT COALESCE(SUM(pm.file_size_bytes), 0)
FROM post_media pm
JOIN posts p ON pm.post_id = p.id
WHERE p.user_id = :userId
  AND p.deleted_at IS NULL;
```

별도 Quota 집계 테이블은 초기 필수 구현이 아니라, 사용량 합산 쿼리가 병목이 되거나 미디어 저장 영역이 게시물 외 영역까지 확장될 때 추후 최적화 방안으로 검토한다.

## 예상 업로드 흐름

```text
[1] Client -> Backend
    Presigned URL 요청

[2] Backend
    인증 확인
    파일 크기 검증
    contentType 검증
    quota 사전 검증
    objectKey 생성
    필요 시 PENDING 상태 저장
    Presigned URL 생성

[3] Backend -> Client
    uploadUrl, objectKey 반환

[4] Client -> MinIO
    uploadUrl로 파일 직접 업로드

[5] Client -> Backend
    업로드 완료 요청

[6] Backend
    MinIO 파일 존재 확인
    실제 파일 크기 확인
    quota 최종 검증
    DB 상태 완료 처리
```

## 환경변수 초안

```env
STORAGE_QUOTA_DEFAULT_LIMIT_BYTES=1073741824
STORAGE_QUOTA_MAX_SINGLE_UPLOAD_BYTES=52428800
STORAGE_QUOTA_PENDING_TTL_SECONDS=900
```

| 변수 | 기본값 | 설명 |
|---|---:|---|
| `STORAGE_QUOTA_DEFAULT_LIMIT_BYTES` | `1073741824` | 사용자 기본 저장 용량 제한, 1GiB |
| `STORAGE_QUOTA_MAX_SINGLE_UPLOAD_BYTES` | `52428800` | 단일 업로드 최대 크기, 50MiB |
| `STORAGE_QUOTA_PENDING_TTL_SECONDS` | `900` | PENDING 업로드 메타데이터 만료 시간, 15분 |

## 확인 필요

- 실제 구현 시 사용자 ID 타입은 머지된 최신 `Member`/DB 스키마 기준을 따른다.
- PENDING 상태를 `post_media`에 둘지, 별도 업로드 메타데이터 테이블에 둘지 결정한다.
- 업로드 완료 confirm API 경로와 요청/응답 형식을 정한다.
- 업로드 실패 또는 PENDING 만료 데이터 정리 정책을 정한다.
