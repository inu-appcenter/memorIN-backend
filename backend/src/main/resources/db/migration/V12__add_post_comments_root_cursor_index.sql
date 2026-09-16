-- 댓글 스레드 커서 페이징(#225)용 인덱스.
--
-- 조회 형태: WHERE post_id = ? AND parent_id IS NULL [AND id > ?] ORDER BY id ASC
--
-- 기존 idx_post_comments_post (post_id, created_at)로는 이 쿼리가 잘 안 맞는다.
-- post_id로 좁힌 뒤 parent_id IS NULL을 필터로 걸러내고 id로 다시 정렬해야 한다.
--
-- 부분 인덱스(WHERE parent_id IS NULL)로 최상위 댓글만 담는다. 대댓글이 많은 게시물일수록
-- 인덱스가 작아지고, 정렬 컬럼(id)이 인덱스의 마지막 컬럼으로 같은 방향에 들어간다.
-- (docs/n+1-audit.md §6-4 체크리스트)
--
-- 대댓글 조회(parent_id IN ...)는 기존 idx_post_comments_parent가 이미 덮는다.
CREATE INDEX IF NOT EXISTS idx_post_comments_root_cursor
    ON post_comments (post_id, id)
    WHERE parent_id IS NULL;
