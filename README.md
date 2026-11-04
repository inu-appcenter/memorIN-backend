# memorIN

> 일상의 순간을 날짜별로 기록하고, 그 기록을 매개로 소통하는 크로스 플랫폼 소셜·메신저 서비스

하루하루의 기록을 타임라인에 쌓고, 그 기록을 채팅방에 공유해 이야기를 나눕니다. iOS·Android 앱과 웹을 하나의 코드베이스로 지원하며, 외부 유료 클라우드나 BaaS 없이 저사양 온프레미스 서버에서 셀프 호스팅할 수 있게 설계했습니다.

| 저장소 | 내용 |
|---|---|
| memorIN-backend (이 저장소) | Spring Boot API 서버, DB 마이그레이션, 로컬 개발용 compose |
| [memorIN-frontend](https://github.com/inu-appcenter/memorIN-frontend) | React Native(Expo) 앱과 웹 |
| [memorIN-deploy](https://github.com/inu-appcenter/memorIN-deploy) | 운영 배포용 compose(GHCR 이미지 사용) |

## 주요 기능

- **기록**: 사진·영상·텍스트 게시물, 달력·타임라인 뷰, 공개 범위(전체·친구·나만), 클라이언트 압축 후 presigned URL로 스토리지에 직접 업로드, 사용자별 저장 용량 할당량
- **소셜**: 팔로우, 친구 피드, 추천 피드, 댓글·이모지 반응, 좋아요, 게시물 검색
- **메신저**: 1:1·그룹 채팅(STOMP), 커서 기반 메시지 페이징, 게시물 공유
- **알림**: 알림 이력, FCM·Web Push 발송(선택)
- **인증**: 이메일 회원가입, JWT access·refresh 토큰

## 아키텍처

```
Client (Expo 앱 / 웹)
   │ REST + JWT        │ STOMP(/ws)        │ presigned URL
   ▼                   ▼                   ▼
Spring Boot 3.5 (Java 17)              Silo (MinIO 호환 S3 스토리지)
   │                   └─▶ FCM / Web Push
   ▼
PostgreSQL 18 (JSONB, Flyway)
```

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Spring Boot 3.5, Spring Security, Hibernate 6, Java 17 |
| Database | PostgreSQL 18, Flyway |
| Storage | Silo(MinIO 커뮤니티 포크, S3 호환) |
| Realtime | Spring STOMP simple broker(SockJS 지원) |
| Push | Firebase Admin(FCM), web-push(VAPID) |
| Infra | Docker Compose, GHCR, pgAdmin 4(로컬) |

## 시작하기 (로컬 개발)

운영 배포는 [memorIN-deploy](https://github.com/inu-appcenter/memorIN-deploy)를 따릅니다.

**사전 요구사항**: Docker와 Docker Compose. Docker 없이 백엔드만 띄우려면 JDK 17.

```bash
cp .env.example .env            # 비밀번호, JWT_SECRET 등을 채운다
docker compose up -d --build    # postgres, minio(Silo), backend
docker compose --profile tools up -d   # pgAdmin까지 함께
```

| 서비스 | 주소 |
|---|---|
| Backend API | http://localhost:8080 |
| 스토리지 S3 API / 콘솔 | http://localhost:9000 / http://localhost:9001 |
| pgAdmin | http://localhost:5050 |
| PostgreSQL | localhost:5432 |

- 스토리지 콘솔 계정은 `.env`의 `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`입니다. 9000·9001은 127.0.0.1에만 열립니다. 와이파이로 붙는 실기기로 테스트하려면 `.env.example`의 `MINIO_BIND_HOST` 안내를 따릅니다.
- pgAdmin 서버 등록과 PG18 `GRANT` 표준은 [`docs/pgadmin-onboarding-grant-guide.md`](docs/pgadmin-onboarding-grant-guide.md)를 봅니다. Host는 `postgres`(서비스명)입니다.

### 푸시 알림 (선택)

기본값은 꺼져 있습니다(`FIREBASE_ENABLED=false`, `WEB_PUSH_ENABLED=false`). 꺼져 있어도 알림은 저장되고 `GET /api/notifications`로 조회됩니다.

- **FCM**: Firebase 서비스 계정 키를 `secrets/firebase-service-account.json`에 두고 `FIREBASE_ENABLED=true`. 컨테이너가 root가 아니므로 `Permission denied`가 나면 `chmod 644`.
- **Web Push**: `npx web-push generate-vapid-keys`로 만든 키를 `.env`에 넣고 `WEB_PUSH_ENABLED=true`.

### Docker 없이 실행

```bash
cd backend
./gradlew bootRun   # 기본 프로필은 H2 인메모리 DB(Flyway 미실행)
./gradlew test      # Testcontainers 테스트는 Docker가 필요하다
```

엔티티가 `jsonb`, 네이티브 ENUM, `uuidv7()` 같은 PostgreSQL 기능을 쓰므로 실제 동작은 compose의 PostgreSQL로 확인합니다.

## 프로젝트 구조

```
backend/
  src/main/java/com/memorin/
    domain/            # 도메인별 controller·service·repository (auth, posts, chat_rooms, notifications ...)
    global/            # 설정, 보안, 예외 처리, 헬스체크, 공통 유틸
  src/main/resources/db/migration/   # Flyway 마이그레이션
  Dockerfile           # 멀티스테이지 빌드, non-root 실행
infra/postgres/        # postgresql.conf, 과거 수동 SQL 기록(참고용)
docs/                  # 설계·명세·운영 문서 (목록: docs/README.md)
docker-compose.yml     # 로컬 개발용 postgres · minio · backend · pgadmin
```

## 기여

- 브랜치는 `develop`에서 만들고 `develop`으로 PR을 올립니다. `main`에는 `develop` → `main` 릴리스 PR로만 반영합니다.
- PR 제목은 `<type>: <설명>` 형식입니다(`feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `style`, `release`).
- 새 DB 변경은 기존 마이그레이션을 고치지 않고 새 버전 파일로 추가합니다([`docs/db-migration-guide.md`](docs/db-migration-guide.md)).

## 라이선스

[GNU Affero General Public License v3.0](LICENSE) (`AGPL-3.0-only`). Copyright (C) 2026 INU AppCenter.

- 이 서버를 수정해 네트워크로 서비스하면, 사용자에게 수정한 소스를 제공해야 합니다(제13조).
- 서드파티 라이브러리는 각자의 라이선스를 따릅니다. 로컬 compose가 쓰는 Silo(`pgsty/silo`)는 AGPL-3.0이며 수정 없이 실행합니다.
