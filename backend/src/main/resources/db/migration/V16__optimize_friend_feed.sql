-- 친구 피드는 팔로잉 테이블을 직접 조인한다. ACCEPTED 관계만 좁혀 posts를 조회하므로
-- 팔로잉 수만큼 UUID를 애플리케이션으로 전송해 IN (...) 절을 만들 필요가 없다.
CREATE INDEX IF NOT EXISTS idx_follows_friend_feed
    ON follows (follower_id, following_id)
    WHERE status = 'ACCEPTED';

-- 친구 피드의 저자별 cursor scan을 위한 부분 인덱스다. PRIVATE 글과 soft-deleted 글은
-- 인덱스에서 제외하며, (user_id, recorded_date, id)는 조인과 keyset cursor를 모두 지원한다.
CREATE INDEX IF NOT EXISTS idx_posts_friend_feed
    ON posts (user_id, recorded_date DESC, id DESC)
    WHERE deleted_at IS NULL
      AND visibility IN ('PUBLIC', 'FRIENDS');
