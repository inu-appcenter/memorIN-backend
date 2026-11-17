package com.memorin.domain.follows.repository;

import com.memorin.domain.follows.service.FollowRecommendCursor;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FollowRecommendationRepository {

    // 공통 친구 1명당 가중치. 최근 활동 점수(0~RECENCY_WINDOW_DAYS)보다
    // 공통 친구 수를 더 우선시하려고 10배로 뒀다. 튜닝은 이 값만 바꾸면 된다.
    private static final long MUTUAL_FRIEND_WEIGHT = 10L;
    // 최근 활동 점수 1일당 가중치.
    private static final long RECENCY_WEIGHT = 1L;
    // 최근 활동으로 인정하는 윈도우(일). 이보다 오래된 게시물이거나
    // 게시물이 아예 없으면 최근 활동 점수는 0이 된다.
    private static final int RECENCY_WINDOW_DAYS = 30;

    private final EntityManager em;

    // viewer_friends: 뷰어의 ACCEPTED 친구 id 집합 (방향 무관, PostAccessPolicy와 동일한 정의)
    //
    // mutual_counts: candidate별 공통 친구 수.
    // follows 테이블 전체를 스캔하며 JOIN ... IN (follower_id, following_id) 형태로 짜면
    // 인덱스를 못 타고 매 요청마다 follows 풀스캔이 된다.
    // 그래서 follower_id IN (...) / following_id IN (...) 두 갈래로 UNION ALL 해서
    // 기존 idx_follows_follower(follower_id, status) / idx_follows_following(following_id, status)
    // 인덱스를 각각 타도록 분리했다 — ChatRoomMemberRepository의 OR 분리 커서 쿼리와 같은 이유.
    //
    // candidates: 최종 후보 풀. 이미 관계(PENDING/ACCEPTED/BLOCKED)가 있는 사용자는
    // Follow_state의 모든 값이 여기 해당하므로, "관계가 하나라도 있으면 제외"로 단순화된다.
    private static final String CANDIDATES_CTE = """
        WITH viewer_friends AS (
            SELECT CASE WHEN f.follower_id = :viewerId THEN f.following_id ELSE f.follower_id END AS friend_id
            FROM follows f
            WHERE f.status = 'ACCEPTED'
              AND (f.follower_id = :viewerId OR f.following_id = :viewerId)
        ),
        mutual_counts AS (
            SELECT candidate_id, COUNT(DISTINCT friend_id) AS mutual_count
            FROM (
                SELECT f.following_id AS candidate_id, f.follower_id AS friend_id
                FROM follows f
                WHERE f.status = 'ACCEPTED'
                  AND f.follower_id IN (SELECT friend_id FROM viewer_friends)
                UNION ALL
                SELECT f.follower_id AS candidate_id, f.following_id AS friend_id
                FROM follows f
                WHERE f.status = 'ACCEPTED'
                  AND f.following_id IN (SELECT friend_id FROM viewer_friends)
            ) edges
            GROUP BY candidate_id
        ),
        last_activity AS (
            SELECT p.user_id, MAX(p.recorded_date) AS last_recorded_date
            FROM posts p
            WHERE p.deleted_at IS NULL
            GROUP BY p.user_id
        ),
        candidates AS (
            SELECT
                u.id AS candidate_id,
                u.display_name,
                u.profile_image_key,
                COALESCE(mc.mutual_count, 0) AS mutual_count,
                (COALESCE(mc.mutual_count, 0) * :mutualWeight
                    + GREATEST(0, :recencyWindowDays - COALESCE(CURRENT_DATE - la.last_recorded_date, :recencyWindowDays)) * :recencyWeight
                ) AS score
            FROM users u
            LEFT JOIN mutual_counts mc ON mc.candidate_id = u.id
            LEFT JOIN last_activity la ON la.user_id = u.id
            WHERE u.deleted_at IS NULL
              AND u.id <> :viewerId
              AND NOT EXISTS (
                  SELECT 1 FROM follows f
                  WHERE (f.follower_id = :viewerId AND f.following_id = u.id)
                     OR (f.follower_id = u.id AND f.following_id = :viewerId)
              )
        )
        """;

    public List<FollowRecommendationRow> recommendFirstPage(UUID viewerId, int limit) {
        String sql = CANDIDATES_CTE + """
            SELECT candidate_id, display_name, profile_image_key, mutual_count, score
            FROM candidates
            ORDER BY score DESC, candidate_id DESC
            LIMIT :limit
            """;
        return runQuery(sql, baseParams(viewerId), limit);
    }

    public List<FollowRecommendationRow> recommendAfterCursor(UUID viewerId, FollowRecommendCursor.Cursor cursor, int limit) {
        String sql = CANDIDATES_CTE + """
            SELECT candidate_id, display_name, profile_image_key, mutual_count, score
            FROM candidates
            WHERE (score, candidate_id) < (:cursorScore, :cursorId)
            ORDER BY score DESC, candidate_id DESC
            LIMIT :limit
            """;
        Map<String, Object> params = baseParams(viewerId);
        params.put("cursorScore", cursor.score());
        params.put("cursorId", cursor.candidateId());
        return runQuery(sql, params, limit);
    }

    private Map<String, Object> baseParams(UUID viewerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("viewerId", viewerId);
        params.put("mutualWeight", MUTUAL_FRIEND_WEIGHT);
        params.put("recencyWeight", RECENCY_WEIGHT);
        params.put("recencyWindowDays", RECENCY_WINDOW_DAYS);
        return params;
    }

    @SuppressWarnings("unchecked")
    private List<FollowRecommendationRow> runQuery(String sql, Map<String, Object> params, int limit) {
        List<Object[]> rows = em.createNativeQuery(sql)
            .unwrap(NativeQuery.class)
            .setProperties(params)
            .setParameter("limit", limit)
            .getResultList();

        return rows.stream()
            .map(row -> new FollowRecommendationRow(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue()
            ))
            .collect(Collectors.toList());
    }
}
