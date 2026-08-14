-- 팔로워/팔로잉/받은요청 목록의 커서 페이지네이션 인덱스
--
-- 대상 쿼리 (FollowRepository.findFollowersWithCursor / findFollowingsWithCursor / findReceivedRequests)
--   WHERE following_id = ? AND status = ? AND id < ?  ORDER BY id DESC  LIMIT 21
--
-- V1의 idx_follows_following (following_id, status) 에는 id가 없다.
-- 그래서 Postgres가 ORDER BY id DESC 를 인덱스로 못 만들고, 조건에 맞는 행을
-- **전부** 읽어 JOIN FETCH까지 끝낸 뒤 정렬하고 나서야 LIMIT을 건다.
-- LIMIT 21이 아무 일도 하지 않는다.
--
-- 실측 (팔로워 5만 명 / follows 15만 행 / 바인드 파라미터 + generic plan):
--
--   | 인덱스 | 1페이지                    | 25,000번째    | 49,000번째    |
--   |--------|----------------------------|---------------|---------------|
--   | 이전   | 40.6ms · 200,663 buffers   | 17.0ms · 100,656 | (더 깊을수록 악화) |
--   |        | (5만 행 전부 JOIN 후 정렬)  |               |               |
--   | 이후   |  0.142ms ·     88 buffers  | 0.079ms · 88  | 0.059ms · 88  |
--
-- 1페이지 기준 버퍼 200,663 → 88개.
-- 이후 버전은 페이지가 깊어져도 88 buffers로 완전히 일정하다 — 팔로워 수와도, 페이지 깊이와도 무관해진다.
--
-- 기존 인덱스는 새 인덱스의 정확한 앞부분(prefix)이라 남겨둘 이유가 없다.
-- (following_id, status) 로 거르는 쿼리는 (following_id, status, id) 인덱스를 그대로 탄다.
-- 중복으로 두면 follows INSERT/UPDATE마다 쓰기 비용만 두 배가 된다.
DROP INDEX IF EXISTS idx_follows_follower;
DROP INDEX IF EXISTS idx_follows_following;

-- 팔로워 목록 + 받은 팔로우 요청 목록 (following_id = 나)
CREATE INDEX IF NOT EXISTS idx_follows_following_id
    ON follows (following_id, status, id DESC);

-- 팔로잉 목록 + 친구 피드의 findFollowingIds (follower_id = 나)
CREATE INDEX IF NOT EXISTS idx_follows_follower_id
    ON follows (follower_id, status, id DESC);
