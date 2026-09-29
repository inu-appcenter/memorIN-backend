-- 채팅 메시지 반응(이모지) 테이블. emoji_type enum은 댓글 이모지 기능에서 이미 만들어졌으므로 재사용한다.

CREATE TABLE message_emoji (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    message_id uuid NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    emoji_type emoji_type NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_message_emoji UNIQUE (user_id, message_id, emoji_type)
);

-- countByMessageIds()가 message_id로 필터링하고 emoji_type으로 GROUP BY 하므로 이 조합을 커버한다.
-- uk_message_emoji는 (user_id, message_id, emoji_type) 순서라 message_id 단독 조회엔 못 쓴다.
CREATE INDEX idx_message_emoji_message ON message_emoji (message_id, emoji_type);
