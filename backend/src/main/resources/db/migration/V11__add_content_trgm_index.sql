-- content(jsonb) 원문 텍스트에 대한 부분 문자열 검색(LIKE '%keyword%')이
-- B-tree 인덱스로는 가속이 안 되므로, pg_trgm 확장 + GIN trigram 인덱스를 추가한다.
-- 검색 쿼리의 LOWER(p.content::text) 표현식과 인덱스 표현식이 정확히 일치해야
-- 플래너가 이 인덱스를 사용한다.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_posts_content_trgm
    ON posts
    USING GIN (LOWER(content::text) gin_trgm_ops);
