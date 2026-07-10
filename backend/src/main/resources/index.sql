-- users
CREATE INDEX IF NOT EXISTS idx_users_email    ON users (email)    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username)  WHERE deleted_at IS NULL;

-- posts
CREATE INDEX IF NOT EXISTS idx_posts_user_date ON posts (user_id, recorded_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_posts_content   ON posts USING GIN (content);

-- post_media
CREATE INDEX IF NOT EXISTS idx_post_media_post ON post_media (post_id, order_index);

-- follows
CREATE INDEX IF NOT EXISTS idx_follows_follower  ON follows (follower_id,  status);
CREATE INDEX IF NOT EXISTS idx_follows_following ON follows (following_id, status);

-- chat_room_members
CREATE INDEX IF NOT EXISTS idx_members_user ON chat_room_members (user_id) WHERE left_at IS NULL;

-- messages
CREATE INDEX IF NOT EXISTS idx_messages_room ON messages (room_id, sent_at DESC) WHERE deleted_at IS NULL;

-- post_likes
CREATE INDEX idx_post_likes_post ON post_likes (post_id);

-- post_comments
CREATE INDEX idx_post_comments_post   ON post_comments (post_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_comments_parent ON post_comments (parent_id)           WHERE parent_id IS NOT NULL;