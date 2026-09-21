# 배포 전 보안 점검 — 백엔드

> 작성: 2026-09-14 (Sprint 5 W11) · 대상: `origin/develop` @ `5ac2753`
> 범위: 백엔드. Sprint 5 배포 게이트 "JWT 만료·재발급, CORS, SQL Injection 보안 점검 완료"의 산출물이다.
> 짝 문서: `docs/auth-jwt-design.md` · `docs/sprint4-architecture-review.md` §2·§8

점검 범위를 계획된 3항목보다 넓게 잡았다. **저장소가 이미 공개돼 있고**(visibility=PUBLIC)
Sprint 5 목표가 셀프호스팅 배포이므로, "누구나 인스턴스를 띄운다"를 전제로 봐야 한다.

기동을 막거나 즉시 악용 가능한 것부터 적는다.

---

## 0. 한눈에

| 심각도 | 건수 | 요지 |
|---|---|---|
| 🔴 차단 | 0 | 즉시 악용 가능하거나 기동을 막는 것은 없다 |
| 🟠 배포 전 처리 권장 | 5 | 토큰 타입 미구분 · 리프레시 토큰 평문 저장 · 레이트 리밋 부재 · 게시물 본문 크기 무제한 · Swagger 무조건 공개 |
| 🟡 알려진 한계로 명시 | 6 | 아래 §4 |
| ✅ 확인됨 | 9 | 아래 §5 |

---

## 1. 🟠 리프레시 토큰이 액세스 토큰으로도 통한다

`JwtTokenProvider`의 두 발급 메서드는 **만료 시간만 다르고 구조가 같다.**

```java
public String createAccessToken(UUID userId) {
    return Jwts.builder().subject(userId.toString()).issuedAt(now).expiration(expiry).signWith(secretKey).compact();
}

public String createRefreshToken(UUID userId) {
    return Jwts.builder().subject(userId.toString()).issuedAt(now).expiration(expiry).signWith(secretKey).compact();
}
```

토큰에 종류를 나타내는 클레임이 없고, `JwtAuthenticationFilter`는 **서명과 만료만** 검사한다.
따라서 `Authorization: Bearer {리프레시 토큰}`으로 **모든 API를 호출할 수 있다.**

무엇이 문제인가 — 액세스 토큰은 15분, 리프레시 토큰은 **7일**이다. 리프레시 토큰은 더 오래 살고
보통 더 느슨하게 보관된다(로컬스토리지 등). 그게 새면 공격자는 15분이 아니라 **7일간 전권**을 갖는다.
액세스 토큰 수명을 짧게 잡아 얻으려던 이득이 통째로 사라진다.

**수정 방향**: 발급 시 `typ` 클레임(`access` / `refresh`)을 넣고, `JwtAuthenticationFilter`는 `access`만,
`AuthService.reissue`는 `refresh`만 받는다. 기존 토큰은 클레임이 없으므로 검증에서 걸린다 —
배포 시 전원 재로그인이 필요하다(액세스 15분·리프레시 7일이라 자연 소멸을 기다려도 된다).

## 2. 🟠 리프레시 토큰이 평문으로 저장된다

```java
refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken));
```

`refresh_tokens` 테이블에 토큰 원문이 그대로 들어간다. **DB가 유출되면 전 사용자의 유효한
리프레시 토큰을 즉시 얻는다.** 비밀번호는 BCrypt로 해시하면서 토큰은 평문인 것은 일관되지 않다.

pgAdmin을 `tools` 프로파일로 띄우는 구조라 DB 열람 경로가 하나 더 있다는 점도 고려해야 한다.

**수정 방향**: 해시(또는 HMAC)해서 저장하고 비교는 해시로 한다. `reissue`의 동등 비교
(`savedToken.getRefreshToken().equals(refreshToken)`)를 해시 비교로 바꾸면 된다.

## 3. 🟠 레이트 리밋이 전혀 없다

`bucket4j` · `Resilience4j` 등 어떤 제한도 없다. 공개 인스턴스에서 바로 문제가 되는 경로:

| 경로 | 악용 |
|---|---|
| `POST /auth/login` | 비밀번호 무차별 대입. 실패 횟수 제한도 계정 잠금도 없다 |
| `POST /auth/signup` | 계정 대량 생성 → 스토리지 할당량을 계정 수만큼 늘려 쓸 수 있다 |
| `GET /api/users/search` · `GET /api/posts/search` | 스크래핑. 검색은 `LIKE '%kw%'`라 인덱스를 못 타므로 비용도 크다 |
| `POST /api/media/presigned` | 예약 남발 |

**온프레미스 저사양 서버가 전제**라는 점에서 더 무겁다. 이번 스프린트에서 구현하지 않기로 한다면
**알려진 한계로 README에 명시**하고, 셀프호스터가 리버스 프록시(nginx `limit_req`) 단에서 걸도록 안내한다.

## 4. 🟠 게시물 본문 크기에 상한이 없다

`PostCreateRequest.content`는 `@NotBlank @ValidJson`만 붙어 있고 `@Size`가 없다.
`application.properties`에도 요청 본문 크기 설정이 없다.

스토리지 할당량(`STORAGE_QUOTA_DEFAULT_LIMIT_BYTES`)은 **MinIO 미디어만** 센다.
DB에 들어가는 `content`(jsonb)는 아무도 세지 않으므로, **사용자 하나가 DB를 채울 수 있다.**

미디어는 그렇게 신경 써서 막아 두고(예약·커밋·재검증·정리 배치) 정작 DB 쪽에 문이 열려 있다.

**수정 방향**: `content`에 `@Size(max = ...)`를 걸고, `spring.servlet.multipart` 및 서버 요청 본문
상한을 명시한다. 값은 "게시물 하나의 본문"으로 합리적인 선에서 정한다.

## 5. ✅ Swagger 운영 노출 제어 — 해결됨 (#261)

`SecurityConfig`의 `/swagger-ui/**` · `/v3/api-docs/**` `permitAll`은 그대로 두되,
`application-docker.properties`에 `springdoc.api-docs.enabled`·`springdoc.swagger-ui.enabled`를
`SWAGGER_ENABLED` 환경변수로 연결했다. docker(배포) 프로파일 기본값은 `false` — 꺼진 상태에서는
springdoc이 엔드포인트 자체를 등록하지 않아 `permitAll` 매처에 도달하기 전에 404가 난다.
로컬 개발 프로파일(`application.properties`)은 영향받지 않고 계속 켜져 있다.

`SwaggerToggleTest`가 꺼진 상태에서 `/v3/api-docs`·`/swagger-ui/index.html`이 200을 주지 않는지
고정한다. `.env.example`에 `SWAGGER_ENABLED` 키와 설명을 추가했다.

---

## 6. 🟡 알려진 한계로 명시할 것

이번 스프린트에서 고치지 않기로 한다면 README 또는 `SECURITY.md`에 적어 둔다.

| # | 항목 | 판단 |
|---|---|---|
| 1 | 로그아웃해도 액세스 토큰은 만료까지 유효 | `logout`은 리프레시 토큰만 지운다. 무상태 JWT의 일반적 트레이드오프이고 15분이면 수용 가능하다. 즉시 무효화가 필요하면 블랙리스트가 필요한데 그건 상태를 다시 들여오는 일이다 |
| 2 | 회원가입 응답으로 계정 존재를 알 수 있다 | 이메일 중복 `USER_002` / 닉네임 중복 `USER_003`으로 갈린다. 중복 확인이 필요한 기능이라 완전히 없앨 수는 없다. **로그인은 잘 돼 있다** — 이메일 없음·비밀번호 불일치 모두 `AUTH_002`로 같다 |
| 3 | 리프레시 토큰 재사용 탐지가 없다 | 회전(rotation)은 하지만 이미 쓴 옛 토큰이 다시 오면 그냥 거절할 뿐, "탈취됐다"로 판단해 세션을 끊지는 않는다 |
| 4 | 사용자당 리프레시 토큰이 1개다 | `refresh_tokens`의 PK가 `user_id`라 기기 하나에서 로그인하면 다른 기기가 밀린다. FCM은 다중 토큰을 지원하는데 인증은 아니다 — 보안 문제라기보다 설계 불일치 |
| 5 | 예외 메시지에 UUID가 실려 나간다 | `handleBusinessException`이 `e.getMessage()`를 그대로 내보내고, 서비스가 `"초대 대상을 찾을 수 없습니다." + memberId` 식으로 붙인다. UUIDv7은 추측이 어려워 실질 위험은 낮지만, 응답으로 특정 UUID의 존재 여부를 확인할 수 있다 |
| 6 | 단일 인스턴스 전제 | STOMP InMemory 브로커와 멤버십 캐시(#210)가 인스턴스마다 따로 존재한다. 여러 대로 늘리면 둘 다 깨진다 (`sprint4-architecture-review.md` §3) |

---

## 7. ✅ 확인 결과 문제 없음

### 7-1. SQL Injection — 현재 안전하다

네이티브 쿼리를 쓰는 곳은 넷이고, 그중 **문자열로 조립하는 것은 `PostSearchRepository` 하나**다.

```java
sql.append(" AND LOWER(p.content::text) LIKE :keywordPattern ESCAPE '\\'");
sql.append(" AND p.tags @> CAST(:tags AS jsonb)");
sql.append(" AND p.timeslot = CAST(:timeslot AS timeslot_type)");
```

**사용자 입력은 전부 이름 있는 파라미터로 바인딩된다** — `viewerId` · `keywordPattern` · `keywordRaw` ·
`tags` · `timeslot` · `cursorId` · `cursorRecordedDate` · `limit`. 문자열로 이어 붙는 부분은
`resolveOrderBy`(enum `PostSortType`에서만 나온다)와 테이블 별칭(`"p"` · `"p2"` 하드코딩)뿐이다.

`LIKE` 패턴의 `%` · `_`도 `escapeLikePattern`으로 이스케이프한다. 유저 검색에서 "`%` 한 글자로
전체 목록이 나가던" 결함(#166)과 같은 종류를 막아 둔 것이다.

> **다만 경계가 관례에 의존한다.** 지금은 안전하지만, `resolveOrderBy`류에 사용자 입력이 한 단계라도
> 닿으면 그 순간 뚫린다. enum 밖의 값이 정렬 표현식으로 들어올 수 없다는 것을 테스트로 고정해 두면 좋겠다.

### 7-2. CORS

`CorsConfig`가 `cors.allowed-origins`를 명시적 목록으로 받고 `"*"`를 쓰지 않는다.
`allowCredentials=false`(JWT를 헤더로 전달, 쿠키 미사용)라 `allowedHeaders("*")`도 안전하다.
WebSocket 핸드셰이크도 **같은 프로퍼티**를 쓴다(`sprint4-architecture-review.md` §8) — 예전에
`setAllowedOriginPatterns("*")`였던 것이 정리됐다.

> 기본값이 `https://memorin.inuappcenter.co.kr`로 박혀 있는 것은 보안 문제는 아니지만
> 셀프호스팅 배포물로서 맞지 않는다 → #229

### 7-3. JWT 만료·재발급

- `Keys.hmacShaKeyFor(secret.getBytes())` — 256비트 미만이면 **기동 시 실패**한다. 약한 키로는 못 뜬다
- `JWT_SECRET` 기본값이 없다(#76·#84). 미주입 시 기동이 막힌다
- 만료(`AUTH_003`)와 서명 불일치(`AUTH_004`)를 구분해 401로 내린다(#172)
- `getAuthentication`이 `findByIdAndDeletedAtIsNull`을 쓴다 → **탈퇴한 사용자의 토큰은 거부된다**
- 재발급은 저장된 토큰과 대조하고 회전시킨다. 탈퇴 사용자는 `deleted_at` 확인에서 거절하고 남은 리프레시 토큰도 삭제한다.

### 7-4. 인증 없이 열린 엔드포인트 — 전부 근거가 있다

| 경로 | 근거 |
|---|---|
| `/auth/signup` · `/auth/login` · `/auth/refresh` | 토큰을 얻기 전 호출해야 한다 |
| `/ws/**` | **의도된 것.** 실제 인증은 STOMP CONNECT에서 한다. HTTP 필터는 핸드셰이크 1회만 지나가고 SockJS 폴백은 헤더를 못 싣는다. 토큰 없이 붙은 소켓은 CONNECT에서 거부되고, CONNECT를 안 보내면 `setTimeToFirstMessage(30초)`가 정리한다 |
| `/swagger-ui/**` · `/v3/api-docs/**` | §5 참조 — 근거는 있으나 선택 가능해야 한다 |
| `/*.html` · `/error` | 정적 자원·오류 경로 |

`/api/media/**`는 permitAll에서 **제외돼 있다.** 열어두면 남의 quota로 업로드가 가능해진다.

### 7-5. 미디어 접근 제어

- 다운로드: `PresignedDownloadService`가 `PostAccessPolicy.assertReadable(post, requesterId)`를 호출한다
- 업로드 커밋: `reservation.isOwnedBy(requesterId)` + 만료 검사 + quota 재검증(#109)

즉 presigned URL을 남의 게시물 미디어로 발급받을 수 없다.

### 7-6. 오류 응답에 내부 정보가 새지 않는다

스택트레이스·내부 경로·SQL이 응답에 실리지 않는다. 예상치 못한 예외는 `COMMON_001` 고정 문구로만
나가고 스택은 서버 로그에만 남는다. (`e.getMessage()`가 나가는 건은 §6-5)

### 7-7. git 히스토리에 시크릿이 없다

저장소가 이미 공개 상태이므로 **전체 히스토리**를 훑었다.

| 패턴 | 결과 |
|---|---|
| `BEGIN RSA PRIVATE KEY` · `BEGIN PRIVATE KEY` · `private_key` | 없음 |
| AWS 액세스 키 · GitHub 토큰 · Slack 토큰 패턴 | 없음 |
| `firebase-service-account.json` 커밋 이력 | 없음 |
| `.env` 커밋 이력 | 없음 |

#76·#84에서 제거한 `jwt.secret` 기본값이 과거 커밋에 남아 있으나 값이
`changeme-in-production-use-256bit-secret` **플레이스홀더**라 실제 노출이 아니다.

> `.gitignore`가 `.env` · `**/firebase-service-account.json` · `*serviceAccount*.json`을 막고 있다.
> PR #237이 `secrets/*`를 추가로 막는다.

### 7-8. 비밀번호

BCrypt(`BCryptPasswordEncoder` 기본 강도 10). 최소 8자(`@Size(min = 8, max = 64)`).
로그인 실패 메시지가 이메일 없음·비밀번호 불일치 모두 `AUTH_002`로 같다 — 계정 열거를 막는다.

### 7-9. WebSocket 인가

CONNECT 인증 + SUBSCRIBE 인가(#209)에 더해, PR #240이 **배달 시점**까지 확인한다.
강퇴·나가기 후 이미 맺어진 구독으로 대화가 새던 창(#210)이 닫힌다.

---

## 8. 후속 이슈

| 심각도 | 항목 | 이슈 |
|---|---|---|
| 🟠 | 토큰 타입 미구분 + 리프레시 토큰 평문 저장 (§1·§2) | #242 |
| 🟠 | 레이트 리밋 부재 (§3) | #243 |
| 🟠 | 게시물 본문 크기 무제한 (§4) | #244 |
| ✅ | Swagger 운영 노출 제어 (§5) | #261에서 해결 |
| 🟡 | 알려진 한계 6건 문서화 (§6) | README / `SECURITY.md` — PM 문서 작업에 포함 |

---

## 9. 결론

**기동을 막거나 즉시 악용 가능한 결함은 없다.** 인증·인가의 뼈대는 서 있고, 스프린트를 거치며
잡아 온 것들(JWT 기본값 제거, CORS 오리진 분리, STOMP 인증·인가, 미디어 접근 제어)이 제 자리에 있다.

배포 전에 손볼 것은 **오래 사는 자격증명의 취급**(§1·§2)과 **공개 인스턴스라는 전제에서 생기는
새 위험**(§3·§4, §5는 #261에서 해결)이다. 앞의 둘은 지금 고치는 편이 싸다 — 토큰 형식이 바뀌면 FE도 함께 움직여야 하는데,
FE 연동이 본격화되기 전인 지금이 그 비용이 가장 작다.

뒤의 셋은 "고칠 것"과 "알려진 한계로 적을 것"을 나누는 판단이 먼저다. 셀프호스팅 프로젝트에서는
**막지 못하는 것을 솔직히 적는 것**도 보안 문서의 일이다.
