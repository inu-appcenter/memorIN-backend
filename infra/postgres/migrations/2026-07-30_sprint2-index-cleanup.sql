-- [기록용 — 더 이상 실행할 필요 없음] Sprint 2 인덱스 정리 (2026-07-30).
-- Flyway 도입(#147) 이후 이 내용은 이미 backend/src/main/resources/db/migration/V1__init.sql에
-- 반영되어 있다(V1은 이 정리가 끝난 이후의 01_init.sql을 그대로 이관한 것). 새 DB는 물론
-- 기존 DB도 V1을 baseline으로 잡으므로 이 파일을 다시 실행할 필요가 없다. 당시 배경과 근거만
-- 남겨두기 위해 보존한다.
--
-- 배경(당시): 스키마 초기화 스크립트(infra/postgres/init/01_init.sql, 현재는 제거됨)는 Postgres
--       데이터 볼륨이 "비어 있을 때만" 실행된다(docker-entrypoint-initdb.d 규칙). 따라서 이미
--       데이터가 있는 DB에는 init 스크립트 변경이 반영되지 않으므로, 인덱스 변경은 이 파일을 직접
--       실행해야 했다. (당시 이 프로젝트는 Flyway/Liquibase 같은 마이그레이션 도구가 없었다.)
--
-- 실행: DB 접근 권한이 있는 관리자가 아래를 psql로 실행.
--       예) psql "$DATABASE_URL" -f 2026-07-30_sprint2-index-cleanup.sql
--
-- 운영/대용량 주의: 아래는 개발 환경 기준의 일반 DDL이다. 트래픽이 있는 DB라면 각 인덱스 생성을
--                   CREATE INDEX CONCURRENTLY 로 바꿔 락을 피한다(단, CONCURRENTLY는 트랜잭션
--                   블록 밖에서 개별 실행해야 하며 실패 시 INVALID 인덱스가 남을 수 있으니 확인 필요).
--
-- 근거: docs/n+1-audit.md 섹션 5-3, 5-4 / 트래킹 이슈 #114

-- 1) 죽은 GIN 인덱스 제거 (content jsonb는 어떤 쿼리 조건에도 미사용)
DROP INDEX IF EXISTS idx_posts_content_gin;

-- 2) 추천 피드 인덱스 추가 (findRecommendationCandidates)
CREATE INDEX IF NOT EXISTS idx_posts_reco
    ON posts (created_at DESC)
    WHERE deleted_at IS NULL AND visibility = 'PUBLIC';

-- 3) 유저 피드 키셋 tie-break: id DESC 포함하도록 재생성
DROP INDEX IF EXISTS idx_posts_user_id;
CREATE INDEX IF NOT EXISTS idx_posts_user_id
    ON posts (user_id, recorded_date DESC, id DESC)
    WHERE deleted_at IS NULL;

-- 4) 댓글 스레드: 부분 인덱스(deleted_at IS NULL) → 비부분으로 교체
--    (스레드 조회는 tombstone 포함 전체를 정렬하므로 부분 인덱스를 못 탄다)
DROP INDEX IF EXISTS idx_post_comments_post;
CREATE INDEX IF NOT EXISTS idx_post_comments_post
    ON post_comments (post_id, created_at);
