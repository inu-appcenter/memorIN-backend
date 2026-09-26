-- Run with a production-shaped, read-only PostgreSQL copy:
--   psql "$DATABASE_URL" -f docs/friend-feed-explain.sql
-- For each target (10, 100, 1000), this selects one viewer with at least that
-- many accepted followings and prints EXPLAIN (ANALYZE, BUFFERS).

PREPARE friend_feed(uuid, date, uuid, integer) AS
SELECT p.*
FROM posts p
JOIN follows f
  ON f.following_id = p.user_id
 AND f.follower_id = $1
 AND f.status = 'ACCEPTED'
WHERE p.deleted_at IS NULL
  AND p.visibility IN ('PUBLIC', 'FRIENDS')
  AND ($2 IS NULL OR (p.recorded_date, p.id) < ($2, $3))
ORDER BY p.recorded_date DESC, p.id DESC
LIMIT $4;

\echo 'Small: viewer with at least 10 accepted followings'
\unset viewer_id
SELECT f.follower_id AS viewer_id
FROM follows f
WHERE f.status = 'ACCEPTED'
GROUP BY f.follower_id
HAVING count(*) >= 10
ORDER BY count(*)
LIMIT 1 \gset
\if :{?viewer_id}
EXPLAIN (ANALYZE, BUFFERS) EXECUTE friend_feed(:'viewer_id'::uuid, NULL, NULL, 21);
\else
\echo 'Skipped: no viewer has 10 accepted followings.'
\endif

\echo 'Medium: viewer with at least 100 accepted followings'
\unset viewer_id
SELECT f.follower_id AS viewer_id
FROM follows f
WHERE f.status = 'ACCEPTED'
GROUP BY f.follower_id
HAVING count(*) >= 100
ORDER BY count(*)
LIMIT 1 \gset
\if :{?viewer_id}
EXPLAIN (ANALYZE, BUFFERS) EXECUTE friend_feed(:'viewer_id'::uuid, NULL, NULL, 21);
\else
\echo 'Skipped: no viewer has 100 accepted followings.'
\endif

\echo 'Large: viewer with at least 1000 accepted followings'
\unset viewer_id
SELECT f.follower_id AS viewer_id
FROM follows f
WHERE f.status = 'ACCEPTED'
GROUP BY f.follower_id
HAVING count(*) >= 1000
ORDER BY count(*)
LIMIT 1 \gset
\if :{?viewer_id}
EXPLAIN (ANALYZE, BUFFERS) EXECUTE friend_feed(:'viewer_id'::uuid, NULL, NULL, 21);
\else
\echo 'Skipped: no viewer has 1000 accepted followings.'
\endif

DEALLOCATE friend_feed;
