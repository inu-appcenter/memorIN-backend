# DB 스키마 마이그레이션 가이드 (Flyway)

> 관련 이슈: #147 (chore: DB 스키마 마이그레이션 도구 도입), #87 (refresh_token 누락 장애), #138/#143 (이모지 API)

## 왜 바뀌었나

기존에는 `infra/postgres/init/01_init.sql` 하나로 스키마를 관리했다. 이 파일은 Postgres의
`docker-entrypoint-initdb.d` 규칙에 따라 **DB 데이터 볼륨이 완전히 비어 있을 때(최초 1회)만** 실행된다.
따라서 이미 기동된 적 있는 볼륨(로컬 개발 DB, 운영 DB)에는 그 이후에 추가한 테이블/컬럼/ENUM이 절대
반영되지 않았다. `refresh_token` 테이블 누락(#87)과 `emoji` 관련 스키마 누락(#147)이 모두 이 구조적
문제 때문에 발생했다.

지금부터 스키마 변경은 전부 **Flyway 마이그레이션**으로만 한다. `docker-entrypoint-initdb.d`
기반 초기화는 완전히 제거했고, `spring.jpa.hibernate.ddl-auto=validate`로 엔티티와 실제 스키마가
어긋나면 애플리케이션이 기동 시점에 바로 실패하도록 했다 (조용히 배포된 뒤 런타임에 `relation ... does
not exist`로 터지는 대신, CI/기동 단계에서 먼저 걸러진다).

## 어디에 있나

```
backend/src/main/resources/db/migration/
├── V1__init.sql            # 기존 01_init.sql 이관 (이모지 제외)
├── V2__add_emoji.sql        # comment_emoji 테이블 + emoji_type ENUM
└── V3__add_fcm_tokens.sql   # fcm_tokens 테이블
```

Flyway는 애플리케이션이 뜰 때 `classpath:db/migration` 아래 파일을 버전 순서대로(V1 → V2 → V3 → ...)
자동 적용한다. 별도로 psql을 손으로 실행할 필요가 없다.

## 새 마이그레이션을 추가하는 절차

1. **파일명 규칙**: `V{다음 번호}__{스네이크_케이스 설명}.sql`
   - 예: 알림 히스토리 테이블을 추가한다면 `V4__add_notification_history.sql`
   - 번호는 반드시 현재 가장 큰 버전 + 1. `backend/src/main/resources/db/migration/`에 있는 파일 중
     가장 큰 `V숫자`를 먼저 확인한다.
2. **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다.** Flyway는 각 마이그레이션 파일의 체크섬을
   `flyway_schema_history` 테이블에 저장해두고, 이미 적용된 파일 내용이 바뀌면 다음 기동 시
   `FlywayValidateException`을 던지고 앱이 뜨지 않는다. 스키마를 고쳐야 하면 고치는 내용을 담은
   **새 버전**을 추가한다(`ALTER TABLE ...`, `DROP ... / CREATE ...` 등).
3. **엔티티와 반드시 같이 맞춘다.** 테이블/컬럼 이름, nullable 여부, ENUM 값은 JPA 엔티티의
   `@Table`/`@Column`/`@Enumerated`와 1:1로 맞아야 한다. 특히:
   - Postgres 네이티브 ENUM 컬럼으로 만들 거면 엔티티 필드에
     `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`을 반드시 같이 붙인다. 빠뜨리면 컬럼은 존재해도
     INSERT 시점에 "column ... is of type ... but expression is of type character varying"
     오류가 난다 (`comment_emoji.emoji_type`이 실제로 이 문제였다 — 도입하면서 같이 고쳤다).
   - `@Column(name = ...)`은 실제 DB 컬럼명과 대소문자까지 정확히 일치해야 한다. Postgres는
     따옴표 없는 식별자를 소문자로 접어버리므로 `lastRead_at` 같은 캐멀케이스는 `lastread_at`으로
     조회되어 실제 컬럼 `last_read_at`과 어긋난다 (`chat_room_members`에서 실제로 있었던 버그).
4. **로컬에서 검증**한다.
   ```bash
   cd backend
   ./gradlew build        # Testcontainers가 새 마이그레이션까지 포함해 실제 Postgres에 적용 + 테스트
   ```
   또는 `docker compose up -d --build`로 실제 compose 환경에 붙여서 backend 로그에 Flyway 적용
   로그(`Migrating schema "public" to version "4 - add notification history"`)가 정상적으로
   찍히는지 확인한다.
5. **PR에 마이그레이션 파일 하나만 있는지 확인.** 한 PR에서 같은 버전 번호를 두 명이 동시에 쓰면
   머지 시 번호가 충돌하니, 머지 직전에 최신 develop/main 기준으로 다음 번호를 다시 확인한다.

## 기존 DB(운영/개발 볼륨)는 어떻게 되나 — Baseline

이미 `01_init.sql`로 초기화되어 돌아가고 있던 DB는 `V1__init.sql`을 다시 실행하면 테이블이 이미
있어 실패한다. 이를 위해 `application-docker.properties`에 Flyway baseline을 설정해뒀다.

```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.baseline-description=init
```

`flyway_schema_history` 테이블이 없는(=Flyway를 한 번도 실행한 적 없는) 기존 DB에 처음 붙으면,
Flyway는 "이 DB는 이미 버전 1(V1__init.sql) 상태까지 적용된 것"으로 간주하고 그 이후 버전(V2, V3, ...)
부터만 실행한다. 즉:

- **기존 DB(볼륨 유지)**: `docker compose up` 시 V2, V3, ... 만 자동 적용됨 → `comment_emoji`,
  `fcm_tokens`가 새로 생긴다.
- **신규 DB(빈 볼륨)**: baseline 없이 V1부터 전부 순서대로 적용됨.

> 로컬 개발 환경에서 `docker compose down -v` 등으로 볼륨을 **완전히 새로** 만든 적이 있다면(특히
> 이모지 API 머지 이후 ~ 이번 Flyway 도입 사이), 옛 `01_init.sql`이 만든 `emoji` 테이블 및
> `emoji_type` ENUM이 이미 존재할 수 있다. 그 경우 `V2__add_emoji.sql`이 `CREATE TYPE emoji_type`에서
> 충돌한다. 로컬 데이터는 버려도 되므로 가장 간단한 해결책은 볼륨을 한 번 더 초기화하는 것이다:
> `docker compose down -v && docker compose up -d --build`.

## ddl-auto=validate 란

`application-docker.properties`의 `spring.jpa.hibernate.ddl-auto`는 `none`에서 `validate`로
바뀌었다. Hibernate가 스키마를 만들거나 고치지 않고, **엔티티 매핑과 실제 DB 스키마가 일치하는지만
기동 시점에 검사**한다. 컬럼이 없거나 타입이 안 맞으면 그 즉시 `SchemaManagementException`으로
기동이 실패한다 — 스키마를 실제로 바꾸는 방법은 오직 위 절차대로 새 Flyway 마이그레이션을 추가하는
것뿐이다. **절대로 `create`/`update`로 바꾸지 않는다** (엔티티가 스키마의 소스가 되어버리면 다시
지금과 같은 드리프트 문제가 재발한다).

## 테스트에서는 어떻게 적용되나

`PostgresTestSupport`(Testcontainers 기반 공통 테스트 베이스)와 개별 Postgres 컨테이너를 쓰는
테스트들은 `spring.flyway.enabled=true` + `ddl-auto=validate`를 명시적으로 켠다. 컨테이너가 뜨면
Spring Boot가 같은 `db/migration` 마이그레이션을 자동으로 적용하므로, 운영과 동일한 스키마 기준으로
테스트가 돈다. 반대로 로컬 `./gradlew bootRun`의 기본(H2) 프로파일은 마이그레이션이 Postgres 전용
문법(`uuidv7()`, 네이티브 ENUM 등)을 쓰기 때문에 `spring.flyway.enabled=false`로 꺼져 있다.
