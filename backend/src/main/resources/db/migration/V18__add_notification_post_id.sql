-- COMMENT 알림의 reference_id는 댓글 id라서 알림만으로는 게시물로 이동할 수 없다.
-- 댓글이 달린 게시물 id를 post_id로 따로 저장한다. 그 외 타입은 NULL이다.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS post_id UUID;

-- 기존 COMMENT 알림은 댓글 id로 게시물 id를 채운다. 댓글 행은 soft delete라 대부분 남아 있다.
UPDATE notifications n
SET post_id = c.post_id
FROM post_comments c
WHERE n.type = 'COMMENT'
  AND n.post_id IS NULL
  AND c.id = n.reference_id;
