# memorIN

> 일상의 순간을 일자별로 기록·아카이빙하고, 이를 매개로 밀도 있는 소통을 지원하는 크로스 플랫폼 소셜 / 메신저 서비스

`memorIN`은 [Setlog](https://setlog.io)처럼 하루하루의 기록을 타임라인에 쌓아두고, 그 기록을 매개로 사람들과 실시간으로 이야기를 나눌 수 있는 서비스입니다. iOS · Android 앱과 데스크탑 웹을 **하나의 코드베이스**로 지원하며, **외부 유료 클라우드 / BaaS 없이 온프레미스 저사양 서버**에서 동작하도록 설계되었습니다. 누구나 직접 **셀프 호스팅(Self-Hosting)** 할 수 있는 오픈소스 지향 프로젝트입니다.

---

## ✨ 핵심 기능

### 일상 아카이빙
- 사진·영상·텍스트를 결합한 타임라인 기반 미디어 기록 (Setlog 데일리 뷰 형태)
- 클라이언트 사전 압축 후 **MinIO로 직접 업로드**(Presigned URL) → 백엔드 서버 부하 최소화
- 게시물별 가변 메타데이터·커스텀 태그는 PostgreSQL `JSONB` 컬럼에 저장
- 달력(Calendar) 뷰 / 세로 스크롤 타임라인 뷰
- 공개 범위 설정(전체 공개 · 친구 공개 · 나만 보기)

### 통합 메신저
- 1:1 및 그룹 채팅방
- 접속 상태: **WebSocket(STOMP / SockJS)** 실시간 메시지 처리
- 미접속 상태: 소켓 반환 후 **FCM / Web Push** 알림으로 전환
- 타임라인의 로그(Log)를 채팅방으로 바로 공유하여 대화 맥락 형성
- 채팅 내역은 비동기 저장, 커서 기반 무한 스크롤 페이징

### 유저 & 인증
- 자체 회원가입(이메일 / 학번 기반)
- **JWT** 기반 경량 인증
- 웹/앱 도메인 간 접근을 위한 CORS 정책
- 유저 검색, 친구 맺기 및 관리

---

## 🏗️ 시스템 아키텍처

```
┌─────────────────────────────────────────────┐
│  Client (단일 코드베이스)                       │
│  React Native (Expo) + React Native for Web   │
│  · 768px 미만 → Stack(Push) 내비게이션          │
│  · 768px 이상 → Split View(Master-Detail)      │
└───────────────┬─────────────────┬─────────────┘
        REST/JWT │   STOMP/SockJS  │ Presigned URL (직접 업로드)
                 ▼                 ▼                 ▼
┌──────────────────────────┐            ┌──────────────────┐
│ Spring Boot 3.x           │            │ MinIO            │
│ · STOMP In-Memory Broker  │            │ (S3 호환 스토리지) │
│ · Hibernate 6.x / JWT     │            └──────────────────┘
└─────────────┬─────────────┘
              ▼
┌──────────────────────────┐            ┌──────────────────┐
│ PostgreSQL 18             │            │ FCM / Web Push    │
│ · io_uring 비동기 I/O      │            │ (백그라운드 알림)  │
│ · JSONB 활용              │            └──────────────────┘
└──────────────────────────┘
```

### 반응형 레이아웃 정책
| 화면 너비 | 내비게이션 | 부가 정보 |
|---|---|---|
| **< 768px** (모바일/세로) | Stack — 화면을 덮으며 Push 이동 | Bottom Sheet |
| **≥ 768px** (데스크탑/가로) | Split View — 좌우 분할 병렬 배치 | 우측 분할 창 고정 |

- 메신저: `[좌 30%] 채팅방 목록` \| `[우 70%] 채팅방 내부`
- 소셜 피드: `[좌 60%] 세로 스크롤 피드` \| `[우 40%] 댓글·반응`

---

## 🧰 기술 스택

| 영역 | 기술 |
|---|---|
| **Frontend** | React Native (Expo), React Native for Web |
| **Backend** | Spring Boot 3.5, Hibernate 6.x, Java 17 |
| **Database** | PostgreSQL 18 (`io_uring`, `JSONB`) |
| **Storage** | MinIO (AWS S3 호환, Docker) |
| **Realtime** | Spring STOMP In-Memory Broker + SockJS Fallback |
| **Push** | Firebase Cloud Messaging (Web Push 포함) |
| **Infra** | Docker Compose, pgAdmin 4 |

> **설계 제약:** 100% 교내 온프레미스 저사양 서버. 외부 유료 클라우드·BaaS 사용 배제. 디스크 병목 해소를 위해 클라이언트 사전 극한 압축 + 유저별 스토리지 할당량(Quota) 정책 도입.

---

## 🚀 시작하기

### 사전 요구사항
- Docker / Docker Compose
- (백엔드 로컬 개발 시) JDK 17

### 1. 환경 변수 설정
```bash
cp .env.example .env
# .env 를 열어 비밀번호·JWT_SECRET 등을 환경에 맞게 수정
```

### 2. Firebase 서비스 계정 키 배치
FCM 알림을 사용하려면 Firebase 콘솔에서 발급한 서비스 계정 키를 아래 경로에 둡니다. (이 파일은 `.gitignore`로 커밋이 차단됩니다.)
```
backend/src/main/resources/firebase-service-account.json
```

### 3. 인프라 + 백엔드 실행
```bash
# postgres + minio + backend
docker compose up -d --build

# pgAdmin 까지 함께 실행하려면 (tools 프로파일)
docker compose --profile tools up -d
```

### 접속 정보 (기본값)
| 서비스 | 주소 |
|---|---|
| Backend API | http://localhost:8080 |
| MinIO Console | http://localhost:9001 |
| pgAdmin | http://localhost:5050 |
| PostgreSQL | localhost:5432 |

### 백엔드 로컬 개발 (Docker 없이)
```bash
cd backend
./gradlew bootRun      # 기본 프로파일은 H2 인메모리 DB 사용
```

---

## 📂 프로젝트 구조

```
.
├── backend/                  # Spring Boot 백엔드
│   ├── src/main/java/com/memorin/
│   │   ├── MemorinApplication.java
│   │   └── config/           # WebSocket, FCM 설정
│   ├── Dockerfile            # 멀티스테이지 빌드 (non-root 실행)
│   └── build.gradle
├── infra/
│   └── postgres/
│       ├── init/             # 초기 DDL 스크립트
│       └── postgresql.conf   # io_uring 등 튜닝 설정
├── docs/
│   └── erd.md                # DB ERD 문서
├── docker-compose.yml        # postgres · minio · backend · pgadmin
└── .env.example
```

---

## 👥 팀 역할 (R&R)

| 파트 | 담당 |
|---|---|
| **Design** | 모바일(Stack)·데스크탑(Split View) 반응형 디자인 시스템, 화면 분할 비율 가이드 |
| **Frontend** | `react-native-web` 세팅, 화면 너비 기반 동적 라우팅, 미디어 압축 모듈, FCM·Service Worker |
| **BE 1 — 유저/인증** | JWT 인증, 친구 도메인 API, 다중 FCM 토큰 저장·갱신 |
| **BE 2 — 인프라/미디어** | PG 18 `io_uring` 세팅, MinIO·Presigned URL, 스토리지 Quota |
| **BE 3 — 채팅/알림** | STOMP/SockJS, In-Memory 브로커 채팅·페이징, HikariCP 튜닝 |
| **BE 4 — 아키텍처/리뷰** | DB 설계 검수(JSONB·스키마 보안), CORS, 소켓 세션/OOM 코드 리뷰 |

---

## ✅ QA 점검 포인트

1. **반응형 상태 동기화** — 데스크탑 → 모바일 전환 시 우측 Detail 뷰가 Stack 최상단으로 자연스럽게 유지되는지
2. **PostgreSQL 18 권한** — `public` 스키마 권한 오류 방지를 위한 표준 DDL·`GRANT` 가이드 준수
3. **WebSocket 생명주기** — 탭 닫기/새로고침 시 인메모리 브로커 세션이 즉시 반환되는지, 누수로 인한 다운이 없는지

---

## 📌 프로젝트 상태

현재 **Sprint 0 (초기 세팅)** 단계입니다. 인프라(Docker Compose), 채팅·FCM 프로토타입, 초안 DDL이 구성되어 있으며 도메인 엔티티 정렬 및 스키마 확정 작업이 진행 중입니다.

---

## 📄 라이선스

추후 결정 예정입니다.
