# PG18 io_uring 검증 결과 (Sprint 0)

> 결론 먼저: **로컬(macOS + Docker Desktop)에서는 io_uring 사용 불가 → `io_method = worker` 유지.**
> io_uring은 운영/스테이징(리눅스 직접 구동) 환경에서만 활성화·검증한다.

---

## 1. io_uring이란

PostgreSQL 18에 새로 들어온 **비동기 I/O(Asynchronous I/O)** 방식이다. 디스크에서 데이터를 읽고 쓸 때:

- **`sync`**: 요청하고 데이터가 올 때까지 프로세스가 멈춰서 대기 (전통 방식)
- **`worker`**: 별도 워커 프로세스들이 I/O를 대신 처리 (PG18 기본값, OS 무관하게 동작)
- **`io_uring`**: 리눅스 커널의 io_uring 인터페이스를 직접 사용. 요청/완료를 커널과 공유하는 링 버퍼로 주고받아 시스템콜 오버헤드가 적고 가장 빠름. **단, 리눅스 전용 + 커널/이미지/보안정책 지원 필요.**

`io_method` 설정은 `infra/postgres/postgresql.conf` 에서 지정한다.

## 2. 검증 절차

```bash
# 1) postgresql.conf 에서 io_method = io_uring 로 변경
# 2) 재기동
docker compose restart postgres
# 3) 상태/적용값 확인
docker compose ps postgres
docker exec memorin_postgres psql -U memorin_user -d memorin_db -tc "SHOW io_method;"
# 4) 실패 시 원인
docker logs memorin_postgres
```

## 3. 검증 결과

`io_method = io_uring` 으로 바꿔 재기동하자 postgres 컨테이너가 **부팅 실패 후 재시작 루프**에 빠졌다.

```
FATAL:  could not setup io_uring queue: Operation not permitted
HINT:   Check if io_uring is disabled via /proc/sys/kernel/io_uring_disabled.
```

단계별 진단:

| 확인 항목 | 결과 | 의미 |
|---|---|---|
| Docker VM 커널 | `6.12.67-linuxkit` | io_uring 기능 자체는 커널에 있음 |
| 이미지 지원 | io_uring 큐 생성 **시도까지 도달** | `postgres:18-alpine`은 io_uring 지원 빌드 |
| 실제 큐 생성 | `Operation not permitted` (EPERM) | **Docker 기본 seccomp가 `io_uring_setup` 시스템콜 차단** |

즉 커널·이미지는 문제없고, **Docker 컨테이너 보안 정책(seccomp)** 이 io_uring 계열 시스템콜을 막는 것이 원인이다. Docker는 io_uring을 커널 공격면 확대 위험으로 보고 기본 프로파일에서 차단한다.

## 4. 결정

- **로컬 개발: `io_method = worker` 유지.** io_uring과의 성능 차이는 로컬에서 체감되지 않으며 worker로 안정 동작한다.
- **io_uring 활성화는 운영/스테이징(리눅스에서 직접 구동, seccomp 제약 없음)에서만 진행·검증.**
- 억지로 로컬에서 켜려면 `docker-compose.yml`에 `security_opt: [seccomp=unconfined]`가 필요하나, 컨테이너 보안을 통째로 푸는 것이라 채택하지 않는다.

> Sprint 0 게이트의 "PG18 io_uring 활성화 검증" 항목은 **본 검증으로 종료**한다
> (로컬 미지원 확인 + 운영 활성화로 결정).
