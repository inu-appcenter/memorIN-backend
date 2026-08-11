# pgAdmin4 온보딩 & PostgreSQL 18 GRANT 가이드

> Sprint 0 — BE 시니어 배포 문서. 팀원 전원(BE)이 로컬에서 동일한 방식으로 DB에 접속하고,
> PG18 `public` 스키마 권한 오류를 방지하기 위한 표준 가이드다.

---

## 0. 전제 (접속 기본값)

`.env.example` 기준 기본값이다. 각자 `.env`에서 값을 바꿨다면 그 값을 사용한다.

| 항목 | 값 |
|---|---|
| pgAdmin URL | http://localhost:5050 |
| pgAdmin 로그인 | `admin@memorin.com` / `admin` (`PGADMIN_EMAIL` / `PGADMIN_PASSWORD`) |
| DB Host (pgAdmin → PG) | `postgres` ← **컨테이너 서비스명. `localhost` 아님** |
| DB Host (호스트 툴 → PG) | `localhost` (DBeaver/psql 등 컨테이너 밖에서 접속 시) |
| DB Port | `5432` (`POSTGRES_PORT`) |
| Database | `memorin_db` (`POSTGRES_DB`) |
| User | `memorin_user` (`POSTGRES_USER`) |
| Password | `memorin_pass` (`POSTGRES_PASSWORD`) |

> ⚠️ **Host 값 주의**: pgAdmin은 `memorin_network` 도커 네트워크 안에서 돈다.
> 그래서 pgAdmin에서 서버를 등록할 때 Host는 `localhost`가 아니라 **서비스명 `postgres`** 를 써야 한다.
> `localhost`를 넣으면 pgAdmin 컨테이너 자기 자신을 찾아가서 접속에 실패한다.

---

## 1. pgAdmin4 실행

pgAdmin은 `tools` 프로파일로 분리되어 있다. 필요할 때만 켠다.

```bash
# 인프라 + 백엔드 + pgAdmin 전부
docker compose --profile tools up -d

# 이미 스택이 떠 있으면 pgAdmin만 추가로
docker compose --profile tools up -d pgadmin
```

브라우저에서 http://localhost:5050 접속 → `admin@memorin.com` / `admin` 로그인.

---

## 2. 서버(Connection) 등록 — 최초 1회

1. 좌측 트리 **Servers** 우클릭 → **Register → Server…**
2. **General** 탭
   - **Name**: `memorIN (local)` — 표시용 이름, 자유
3. **Connection** 탭
   - **Host name/address**: `postgres`
   - **Port**: `5432`
   - **Maintenance database**: `memorin_db`
   - **Username**: `memorin_user`
   - **Password**: `memorin_pass`
   - **Save password?**: 체크 (로컬 개발용)
4. **Save** → 좌측 트리에 서버가 뜨고 자동 접속된다.

접속 후 스키마 확인 경로:
`Servers → memorIN (local) → Databases → memorin_db → Schemas → public → Tables`
여기에 Flyway 마이그레이션(`backend/src/main/resources/db/migration`)으로 생성된 `users`, `posts`, `follows`, `chat_rooms`, `messages` 등이 보이면 정상이다.

> 스키마는 더 이상 `docker-entrypoint-initdb.d` 초기 DDL이 아니라 Flyway가 관리한다 — 백엔드 컨테이너가
> 기동할 때마다 아직 적용 안 된 마이그레이션(`V숫자__설명.sql`)을 자동으로 실행한다. 자세한 절차는
> [`db-migration-guide.md`](db-migration-guide.md) 참고.
> 테이블이 안 보이면 backend 컨테이너 로그에서 Flyway 마이그레이션이 실패하지 않았는지 먼저 확인한다.
> 로컬 개발 데이터는 버려도 된다면 볼륨을 초기화하고 다시 올려도 된다:
> ```bash
> docker compose down -v && docker compose up -d
> ```
> `down -v`는 **DB 데이터를 전부 삭제**하므로 초기 세팅 단계에서만 사용한다.

---

## 3. PostgreSQL 18 `public` 스키마 권한 — 왜 GRANT가 필요한가

**PG15부터 바뀐 동작**: 예전(≤PG14)에는 `public` 스키마에 대해 모든 롤(`PUBLIC`)이 `CREATE` 권한을 기본으로 가졌다.
PG15+부터는 **보안 강화를 위해 `PUBLIC`의 `CREATE` 권한이 회수**됐다. PG18도 동일하다.

결과적으로:
- **DB 소유자(우리 경우 `memorin_user`)** 는 여전히 `public`에 테이블을 만들 수 있다 → 그래서 지금 초기 DDL은 문제없이 실행된다.
- 하지만 **나중에 별도 앱 롤/읽기전용 롤/추가 개발자 롤**을 만들면, 그 롤은 기본적으로 `public`에 아무 권한이 없어
  `permission denied for schema public` 오류가 난다.

이 가이드의 GRANT 표준은 그 오류를 예방하기 위한 것이다.

---

## 4. 표준 GRANT DDL

> 실행 위치: pgAdmin에서 `memorin_db` 선택 → 상단 **Query Tool**(번개⚡ 아이콘) → 아래 SQL 붙여넣고 실행(F5).
> **소유자(`memorin_user`)로 접속한 세션에서 실행**해야 한다.

### 4-1. (참고) 현재 단일 유저 구조

지금 Sprint 0에서는 `memorin_user` 하나로 앱·마이그레이션·조회를 모두 처리한다.
`memorin_user`는 DB 소유자라 별도 GRANT 없이 동작한다. 아래 롤 분리는 **권장 표준**이며, 보안을 조일 때 적용한다.

### 4-2. 애플리케이션 롤 (읽기/쓰기)

```sql
-- 앱 전용 롤 (예시). 실제 비밀번호는 .env로 관리
CREATE ROLE memorin_app LOGIN PASSWORD 'change-me-app-password';

-- 스키마 사용/생성 권한
GRANT USAGE  ON SCHEMA public TO memorin_app;
GRANT CREATE ON SCHEMA public TO memorin_app;   -- Hibernate가 스키마 조작할 경우에만. 운영에선 보통 회수

-- 기존 테이블/시퀀스 권한
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA public TO memorin_app;
GRANT USAGE, SELECT                  ON ALL SEQUENCES  IN SCHEMA public TO memorin_app;

-- 앞으로 소유자가 만들 테이블/시퀀스에도 자동 부여 (default privileges)
ALTER DEFAULT PRIVILEGES FOR ROLE memorin_user IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO memorin_app;
ALTER DEFAULT PRIVILEGES FOR ROLE memorin_user IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO memorin_app;
```

### 4-3. 읽기전용 롤 (조회·리뷰·분석용)

```sql
CREATE ROLE memorin_readonly LOGIN PASSWORD 'change-me-ro-password';

GRANT USAGE  ON SCHEMA public TO memorin_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO memorin_readonly;

ALTER DEFAULT PRIVILEGES FOR ROLE memorin_user IN SCHEMA public
    GRANT SELECT ON TABLES TO memorin_readonly;
```

### 4-4. ENUM 등 커스텀 타입 사용 권한

초기 마이그레이션(`V1__init.sql`)은 `visibility_type`, `follow_status` 등 커스텀 ENUM을 만든다.
비소유 롤이 이 타입이 걸린 테이블에 INSERT/UPDATE 하려면 타입 `USAGE`가 필요하다.

```sql
GRANT USAGE ON TYPE visibility_type TO memorin_app;
GRANT USAGE ON TYPE follow_status   TO memorin_app;
GRANT USAGE ON TYPE chat_type       TO memorin_app;
GRANT USAGE ON TYPE member_role     TO memorin_app;
```

---

## 5. DDL 작성 표준 (스키마 확정 이후 준수)

수요일 스키마 리뷰에서 DDL이 확정되면, 모든 마이그레이션/DDL은 아래를 지킨다.

1. **스키마 명시**: `CREATE TABLE public.<name>` 처럼 `public.` 을 명시하거나, 세션 `search_path`를 `public`으로 고정한다.
2. **소유권 일관성**: 테이블은 `memorin_user`(소유자)가 만든다. 그래야 4-2/4-3의 default privileges가 적용된다.
3. **권한은 DDL 뒤에 GRANT 블록으로**: 테이블을 만든 스크립트 끝에 필요한 GRANT를 함께 둔다.
4. **`GRANT ... TO PUBLIC` 금지**: PG15+ 보안 기본값을 되돌리는 셈이라 사용하지 않는다. 롤 단위로만 부여한다.

---

## 6. 자주 겪는 오류 & 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| pgAdmin 컨테이너가 계속 `Restarting` | `PGADMIN_EMAIL`이 `.local` 등 예약 도메인 → 최신 pgAdmin4가 이메일 거부 | `.env`의 `PGADMIN_EMAIL`을 실제 도메인(`admin@memorin.com` 등)으로. 그 뒤 `docker compose --profile tools up -d --force-recreate pgadmin` |
| pgAdmin 서버 등록 시 `could not connect` | Host를 `localhost`로 넣음 | Host를 **`postgres`** 로 변경 |
| `permission denied for schema public` | 비소유 롤이 `public`에 접근 | 4-2의 `GRANT USAGE/CREATE ON SCHEMA public` 실행 |
| `permission denied for table users` | 테이블 권한 미부여 | 4-2의 `GRANT SELECT,INSERT,... ON ALL TABLES` 실행 |
| 새로 만든 테이블만 권한 없음 | default privileges 미설정 | 4-2 `ALTER DEFAULT PRIVILEGES` 실행 |
| `type "visibility_type" ... permission denied` | ENUM 타입 USAGE 없음 | 4-4의 `GRANT USAGE ON TYPE` 실행 |
| pgAdmin에 테이블이 안 보임 | init DDL이 볼륨 존재로 스킵됨 | `docker compose down -v && up -d` (데이터 삭제 주의) |
| 비밀번호 틀림 | `.env`와 pgAdmin 등록값 불일치 | `.env`의 `POSTGRES_*` 값과 맞춤 |

---

## 7. 체크리스트 (온보딩 완료 기준)

- [ ] `docker compose --profile tools up -d` 로 pgAdmin 기동
- [ ] http://localhost:5050 로그인 성공
- [ ] Host `postgres`로 서버 등록 → `memorin_db` 접속 성공
- [ ] `public` 스키마에서 초기 테이블(users/posts/...) 확인
- [ ] (권한 분리 적용 시) 4장 GRANT 블록 실행 후 앱 롤로 CRUD 확인
