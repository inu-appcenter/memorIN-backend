-- 댓글 이모지 반응 (feat: 이모지 API 제작, PR #143)
--
-- 참고: infra/postgres/init/01_init.sql에 같은 커밋으로 추가됐던 버전은 테이블명이
-- "emoji"였다. 하지만 CommentEmoji 엔티티(@Table)는 처음부터 "comment_emoji"로
-- 매핑되어 있었다 — 이후 채팅 이모지(ChatEmoji, 아직 미구현) 등 반응 대상별로
-- 테이블을 분리할 계획이라 "무엇에 대한 이모지인지"가 이름에 드러나야 하기 때문이다.
-- 이번에 ddl-auto=validate를 켜면서 엔티티 기준으로 이름을 맞춘다.
CREATE TYPE emoji_type AS ENUM ('HEART', 'DISLIKE', 'LIKE', 'NO', 'CHECK', 'FIRE');

CREATE TABLE comment_emoji (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id     UUID        NOT NULL REFERENCES users(id),
    comment_id  UUID        NOT NULL REFERENCES post_comments(id) ON DELETE CASCADE,
    emoji_type  emoji_type  NOT NULL DEFAULT 'HEART',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_comment_emoji UNIQUE (user_id, comment_id, emoji_type)
);
