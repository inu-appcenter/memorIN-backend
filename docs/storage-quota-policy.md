# Storage Quota 정책

## 개요

사용자별 미디어 저장 용량을 제한한다. 사용량은 별도 집계 테이블 없이 두 테이블의 합산으로 매번 계산한다.

``` java
usedBytes = SUM(post_media.file_size_bytes)      // committed: 게시물에 첨부 완료된 미디어
          + SUM(pending_uploads.reserved_bytes)  // pending: 만료 전, 아직 첨부되지 않은 예약
```

(`StorageQuotaService.getUsedBytes`)

별도 집계 테이블(`user_storage_quotas` 등)은 이 합산 쿼리가 병목이 되거나 미디어 저장 영역이 게시물 외 영역까지 확장될 때 도입을 검토한다.

## 업로드 흐름과 검증 시점

```text
[1] POST /api/media/presigned-upload-url        (PresignedUploadService)
    - 콘텐츠 타입 / 클라이언트 선언 contentLength 검증
    - StorageQuotaService.reserveUpload:
        유저 행 잠금 -> committed+pending 합산 -> 한도 체크 -> pending_uploads 삽입
        (전부 하나의 트랜잭션)
    - presigned PUT URL 발급

[2] Client -> MinIO
    presigned URL로 파일을 직접 PUT (백엔드는 바이트를 거치지 않는다)

[3] 게시물 생성/수정 시 첨부 커밋           (PostService.saveMedia -> MediaUploadCommitService.commitUpload)
    - pending 예약 소유자/만료 확인
    - MinIO statObject로 실제 업로드 크기(actualBytes) 확인
    - actualBytes가 단일 파일 상한(MinioProperties.maxUploadSizeBytes) 이하인지 확인
    - StorageQuotaService.assertCommitWithinLimit:
        유저 행 잠금 -> 이 예약의 선언값(reservedBytes)을 actualBytes로 대체해 committed+pending 재합산 -> 한도 재검증
        (전부 하나의 트랜잭션)
    - pending_uploads 행 삭제, post_media.file_size_bytes에 actualBytes 기록
```

커밋 시점엔 클라이언트가 선언한 `contentLength`를 전혀 신뢰하지 않고 MinIO 실측값만 사용한다. 즉 최종 `file_size_bytes`는 항상 실제 업로드된 바이트 수다.

presigned PUT은 서명이 body 크기를 강제하지 않으므로, 예약 시 선언한 값보다 큰(단, 단일 파일 상한 이내인) 파일을 실제로 업로드할 수 있다. 이 차이가 유저 전체 한도(`STORAGE_QUOTA_DEFAULT_LIMIT_BYTES`)를 넘기지 않도록, 커밋 단계에서도 실제 크기 기준으로 전체 quota를 다시 검증한다. 단일 파일 상한 검증만으로는 이 경로를 막을 수 없다.

## 동시성 보장 (TOCTOU 방지)

`StorageQuotaService.reserveUpload`와 `assertCommitWithinLimit`는 각각 `userRepository.findByIdForUpdate`(`SELECT ... FOR UPDATE`)로 유저 행을 잠근 뒤, 같은 트랜잭션 안에서 사용량 조회 → 한도 체크(→ 예약 삽입 / 예약 삭제)를 수행한다. 같은 유저의 동시 요청은 이 행 락에서 직렬화되므로 "잔여 100MB에 90MB 예약 2건이 동시에 들어와 둘 다 통과"하는 경쟁 상태가 생기지 않는다.

- 원자적 `UPDATE ... WHERE usage + size <= limit` SQL 방식이 아니라, 행 락 + 단일 트랜잭션으로 직렬화하는 방식을 택했다.
- 낙관적 락(`@Version`)이나 분산 락(Redis 등)은 쓰지 않는다.
- 다른 유저 간에는 락이 걸리지 않는다.
- 회귀 테스트: `backend/src/test/java/com/memorin/global/media/service/StorageQuotaConcurrencyTest.java` — 실제 Postgres 컨테이너로 동시 요청 2건 중 정확히 1건만 성공함을 검증한다(이슈 #70).

## 만료된 예약 정리

커밋 없이 `pendingTtlSeconds`가 지난 예약은 `PendingUploadCleanupJob`(주기: `storage.quota.pending-cleanup-interval-ms`, 기본 5분)이 MinIO 오브젝트와 `pending_uploads` 행을 함께 정리한다. MinIO 삭제가 실패하면 DB 행은 남겨 다음 주기에 재시도한다.

## 환경 변수

| 이름 | 기본값 | 설명 |
|---|---:|---|
| `STORAGE_QUOTA_DEFAULT_LIMIT_BYTES` | `1073741824` (1GiB) | 사용자 전체 저장 용량 한도 |
| `STORAGE_QUOTA_PENDING_TTL_SECONDS` | `900` (15분) | pending 예약이 커밋 없이 유지되는 최대 시간 |
| `STORAGE_QUOTA_WARNING_THRESHOLD_PERCENT` | `80` | 사용률이 이 값(%) 이상이면 `GET /api/media/quota` 응답의 `warning`이 `true`가 된다 |
| `MINIO_MAX_UPLOAD_SIZE_BYTES` | `52428800` (50MiB) | **단일 파일** 상한. 전체 quota(`STORAGE_QUOTA_DEFAULT_LIMIT_BYTES`)와 별개 설정이다 |

## 사용량 조회 (대시보드)

`GET /api/media/quota`가 `usedBytes`/`totalQuotaBytes`/`remainingBytes`/`usagePercent`/`warning`을 반환한다(`StorageQuotaService.getQuotaStatus`). 요청/응답 예시는 `docs/api-spec.md` §4-4를 기준으로 한다.


## 관련 문서

- `docs/api-spec.md` §4: Presigned Upload / 업로드 커밋 / 압축 가이드 정책 API 명세.
- `docs/minio-bucket-policy.md`: MinIO private bucket 운영 정책.
