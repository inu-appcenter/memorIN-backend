# JWT 인증 구조 설계 (초안)

> Sprint 0 설계 초안 (2026-07-02).
> ✅ = 추천안 확정 가능, 🔶 = **팀/PM 결정 필요** — 결정되면 이 문서를 갱신하고 이슈에 코멘트로 기록할 것.

---

## 1. 목적과 범위

- 로그인 성공 시 JWT(Access/Refresh)를 발급하고, 이후 API 요청을 토큰으로 인증한다.
- 이 문서는 **토큰 구조·발급·검증·재발급·무효화**까지를 다룬다. 회원가입 상세(검증 규칙 등)와 이메일 인증은 별도 절(§8)에 착수 수준으로만 정리.
- 관련 코드: `SecurityConfig`(현재 `permitAll`), `AuthController`(빈 클래스), `Member` 엔티티, jjwt 0.12.7 의존성 (모두 develop에 존재).

### 1-1. 먼저 용어 한 줄씩

처음 보는 단어가 많을 테니 딱 필요한 만큼만 정리한다.

- **JWT** — 서버가 서명해서 발급하는 "출입증" 문자열. 안에 "이 사람은 회원번호 3번, 유효기간 15분" 같은 정보가 들어 있고, 서명 덕분에 위조하면 바로 티가 난다.
- **Access Token** — 매 API 요청에 들고 다니는 짧은 수명(15분)의 출입증.
- **Refresh Token** — Access가 만료됐을 때 "새 출입증 주세요" 할 때만 쓰는 긴 수명(7일)의 교환권.
- **BCrypt** — 비밀번호를 원문 그대로 DB에 저장하면 유출 시 끝장이라, 되돌릴 수 없는 형태로 뭉개서(해시) 저장하는 표준 방법. 로그인할 때는 입력값을 같은 방식으로 뭉개서 비교한다.
- **필터(Filter)** — 모든 요청이 컨트롤러에 도착하기 전에 거치는 검문소. JWT 검사를 여기서 한다.
- **Stateless** — 서버가 "누가 로그인 중인지"를 세션으로 기억하지 않고, 매 요청의 토큰만 보고 판단하는 방식. 서버를 여러 대로 늘려도 문제가 없어서 이 방식을 쓴다.

### 1-2. 지금 코드가 어디까지 와 있나 (2026-07-02 develop 기준)

**이미 있는 것** — 재료는 준비됨:

| 있는 것 | 위치 | 상태 |
|---|---|---|
| JWT 라이브러리 (jjwt 0.12.7) | `build.gradle` | 의존성만 추가됨, 쓰는 코드 없음 |
| Redis 라이브러리 | `build.gradle` | 위와 동일 |
| Spring Security 기본 설정 | `config/SecurityConfig.java` | **모든 요청 통과(`permitAll`)** — 잠금장치를 사놓고 문을 열어둔 상태 |
| 인증 컨트롤러 자리 | `auth/controller/AuthController.java` | `/auth` 경로만 잡혀 있는 빈 클래스 |
| 회원 엔티티 + 리포지토리 | `member/` | `email`, `passwordHash` 필드까지 준비됨 |
| 만료시간·시크릿 환경변수 | `.env.example` | 값은 정해져 있는데 읽어가는 코드가 없음 |

**아직 없는 것** — 이 문서가 설계하는 대상이 바로 이것들:

| 없는 것 | 왜 필요한가 |
|---|---|
| `JwtTokenProvider` (토큰 발급·검증 클래스) | 출입증을 "만들고" "진짜인지 검사하는" 기계. 이게 없으면 JWT의 J도 시작 못 한다 |
| `JwtAuthenticationFilter` (검문소) | 매 요청의 `Authorization` 헤더에서 토큰을 꺼내 검사하는 곳. 이게 없으면 토큰을 발급해도 아무도 검사하지 않는다 |
| `POST /auth/signup` (회원가입 API) | `Member` 테이블에 넣어줄 입구가 없어서 지금은 회원이 생길 방법 자체가 없다 |
| `POST /auth/login` (로그인 API) | 비밀번호를 대조하고 토큰을 발급하는 곳 |
| `PasswordEncoder` 빈 (BCrypt) | 비밀번호를 안전하게 저장/대조하는 도구. 없으면 비번을 원문으로 저장하게 되는데, 절대 금지 |
| 재발급/로그아웃 API | 15분마다 재로그인시킬 수는 없으니 필요 (§5-3, §5-4) |
| Redis 연결 설정 + docker-compose 서비스 | Refresh Token 보관소 (§4에서 Redis로 확정 시) |

> 요약: **재료(의존성·엔티티·환경변수)는 다 사놨는데 요리(실제 인증 로직)는 시작 전이다.** 그리고 현재 `permitAll` 상태라 presigned URL 발급 API가 로그인 없이 아무나 호출 가능한 상태 — §6에서 잠근다.

## 2. 로그인 식별자 🔶

| 선택지 | 내용 | 영향 |
|---|---|---|
| **(현재 진행 기준) 이메일 + 비밀번호** | `Member.email` (UNIQUE) 사용 | 추가 작업 없음 |
| (a) 학번을 아이디로 사용 | `Member`에 `student_id` UNIQUE 컬럼 추가 | 컬럼 1개 + 로그인 조회 조건 변경. JWT 구조 영향 없음 |
| (b) 학교 포털 계정 연동 인증 | 외부 시스템에 자격 검증 위임 | 로그인 단계만 교체, "검증 성공 → 우리 JWT 발급" 구조는 동일 |

> 어느 쪽으로 확정되든 **토큰 발급 이후의 구조(§3~§7)는 변하지 않는다.** 따라서 결정을 기다리지 않고 구현을 진행한다.

## 3. 토큰 전략 ✅

`.env.example`에 이미 정의된 값을 기준으로 한다.

| 항목 | Access Token | Refresh Token |
|---|---|---|
| 만료 | **15분** (`JWT_ACCESS_EXPIRY_MS=900000`) | **7일** (`JWT_REFRESH_EXPIRY_MS=604800000`) |
| 용도 | 매 API 요청 인증 | Access 만료 시 재발급 전용 |
| 전달 위치 | `Authorization: Bearer <token>` 헤더 | 응답 body (모바일 앱 기준, §9-3 참고) |
| 서버 저장 | 저장 안 함 (stateless) | 저장함 — §4 |

- 서명: HS256, 시크릿은 `JWT_SECRET` 환경변수 (256비트 이상, 코드/리포에 절대 커밋 금지)
- Access 클레임: `sub`(memberId), `iat`, `exp` — 최소한만. 권한(role)은 필요해지면 추가
- 라이브러리: jjwt 0.12.7 (`Jwts.builder()` / `Jwts.parser()` 신 API 사용)

## 4. Refresh Token 저장소 🔶

| 선택지 | 장점 | 단점 |
|---|---|---|
| **Redis (추천)** | 의존성 이미 추가됨, TTL 자동 만료, 로그아웃=키 삭제로 간단 | 로컬 개발 시 Redis 컨테이너 필요 (docker-compose에 서비스 추가) |
| DB 테이블 (`refresh_tokens`) | 인프라 추가 없음 | 만료 토큰 정리 배치 필요, 매 재발급마다 DB 조회 |

추천: **Redis**, 키 구조 `refresh:{memberId}` → 토큰 값, TTL 7일. 1인 1토큰(재로그인 시 덮어씀)으로 시작하고, 기기별 다중 세션은 필요해지면 `refresh:{memberId}:{deviceId}`로 확장.

## 5. 플로우

### 5-1. 로그인 (`POST /auth/login`)
```
클라이언트 → 이메일+비번 → AuthService
  1. Member 조회 → BCrypt로 비밀번호 대조
  2. 성공: Access + Refresh 발급, Refresh를 Redis에 저장
  3. 응답: { accessToken, refreshToken }
  실패: 401 (아이디/비번 어느 쪽이 틀렸는지 구분해서 알려주지 않음)
```

### 5-2. 인증이 필요한 API 요청
```
클라이언트 → Authorization: Bearer <access> → JwtAuthenticationFilter
  유효   → SecurityContext에 인증 정보 세팅 → 컨트롤러 진입
  만료   → 401 + 에러코드 (클라이언트는 5-3으로)
  위조/누락 → 401
```

### 5-3. 재발급 (`POST /auth/reissue`)
```
클라이언트 → refreshToken → AuthService
  1. 토큰 서명·만료 검증
  2. Redis의 저장값과 일치 확인 (탈취된 옛 토큰 차단)
  3. Access + Refresh 모두 새로 발급 (Refresh Rotation), Redis 갱신
  불일치/만료 → 401 → 클라이언트는 재로그인
```

### 5-4. 로그아웃 (`POST /auth/logout`)
```
Redis에서 refresh:{memberId} 삭제 → 이후 재발급 불가
(Access는 만료 15분까지 유효 — 짧아서 허용. 즉시 차단이 필요해지면 블랙리스트 추가 검토)
```

## 6. Security 설정 변경 (SecurityConfig)

- 세션 정책 `STATELESS`, `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록
- `PasswordEncoder` 빈 = BCrypt
- 경로 규칙:

| 경로 | 정책 |
|---|---|
| `POST /auth/signup`, `/auth/login`, `/auth/reissue` | permitAll |
| `/ws/**`, `/test.html` (STOMP 테스트) | 당분간 permitAll — WebSocket 토큰 인증은 Sprint 1에서 별도 설계 |
| `POST /api/media/presigned-upload-url` | **authenticated** ← 현재 무인증으로 열려 있음, 필터 적용 시 최우선 보호 |
| 그 외 전부 | authenticated |

## 7. 구현 순서 (PR 단위 제안)

§1-2의 "없는 것" 표를 순서대로 채우는 계획이다. 한 번에 다 만들려 하지 말고 PR 하나당 한 단계씩.

1. **회원가입** — `POST /auth/signup`: `SignupRequest` 검증 어노테이션 추가(현재 없음), BCrypt 해시 후 저장
2. **로그인 + 토큰 발급** — `JwtTokenProvider`(발급/검증), Redis 저장, `/auth/login`
3. **필터 적용** — `JwtAuthenticationFilter` + SecurityConfig 경로 규칙 (이 시점에 presigned URL 보호됨)
4. **재발급/로그아웃** — `/auth/reissue`, `/auth/logout`

각 단계가 독립적으로 머지 가능하고, 1~2까지만 돼도 FE가 로그인 연동을 시작할 수 있다.

## 8. 이메일 인증 (착수 메모)

- `Member.emailVerified`(boolean, 현재 주석 처리됨) 활성화
- 가입 시 인증 메일 발송 → 토큰 포함 링크 클릭 → `emailVerified=true`
- 🔶 발송 수단(SMTP? 학교 메일 정책?)과 **미인증 사용자의 허용 범위**(로그인 자체 차단 vs 기능 제한)는 PM 결정 필요
- 학번 로그인 (b)안으로 확정되면 이메일 인증이 불필요해질 수 있음 → §2 결정 전에는 구현 착수 보류 권장

## 9. 팀 결정 필요 항목 요약 🔶

1. **로그인 식별자** — 이메일 유지 / (a) 학번 아이디 / (b) 포털 연동 (§2)
2. **Refresh 저장소** — Redis(추천) / DB (§4)
3. **모바일 토큰 저장 가이드** — RN(Expo) 기준 SecureStore 사용 권장, FE와 합의 필요
4. **이메일 인증 범위** — §8
