# docs

## 기준 문서
| 문서 | 내용 |
|---|---|
| [api-spec.md](api-spec.md) | 공통 규칙, 인증(JWT), presigned 업로드 API |
| [api-spec-domains.md](api-spec-domains.md) | 유저·게시물·댓글·이모지·팔로우·알림·채팅 API |
| [erd.md](erd.md) | DB ERD |
| [storage-quota-policy.md](storage-quota-policy.md) | 사용자별 저장 용량 할당량 |
| [minio-bucket-policy.md](minio-bucket-policy.md) | 스토리지 버킷 정책 |
| [security-review.md](security-review.md) | 배포 전 보안 점검 기록 (§7-4·§7-9는 #304로 정정 예정) |

## 개발 가이드
| 문서 | 내용 |
|---|---|
| [db-migration-guide.md](db-migration-guide.md) | Flyway 마이그레이션 작성 절차 |
| [pgadmin-onboarding-grant-guide.md](pgadmin-onboarding-grant-guide.md) | pgAdmin 접속, PG18 `GRANT` 표준 |
| [n+1-audit.md](n+1-audit.md) | 목록 API N+1 점검 기준 |
| [auth-jwt-design.md](auth-jwt-design.md) | JWT 인증 설계 배경 |

## 측정·점검 기록
작성 시점의 코드 기준이며 갱신하지 않는다.

| 문서 | 내용 |
|---|---|
| [hikari-pool-load-test.md](hikari-pool-load-test.md) | HikariCP 풀 부하 실측 (#231) |
| [ws-stress-test.md](ws-stress-test.md) | WebSocket 스트레스 테스트 |
| [controller-test-coverage.md](controller-test-coverage.md) | 컨트롤러 경유 테스트 커버리지 (#204) |
| [pg18-io_uring-검증.md](pg18-io_uring-검증.md) | PG18 io_uring 검증 |

## 스프린트·배포 기록
당시 결정과 배경을 보존하는 문서다. 현재 동작은 위 기준 문서를 따른다.

- [sprint2-wrapup.md](sprint2-wrapup.md), [sprint3-wrapup.md](sprint3-wrapup.md), [sprint4-wrapup.md](sprint4-wrapup.md)
- [sprint4-architecture-review.md](sprint4-architecture-review.md), [sprint4-code-review.md](sprint4-code-review.md)
- [domain-interface-draft.md](domain-interface-draft.md): Sprint 0 도메인 경계 초안
- [auth-token-rollout.md](auth-token-rollout.md), [fe-api-change-notice.md](fe-api-change-notice.md): 프론트 동시 배포가 필요했던 변경 공지
