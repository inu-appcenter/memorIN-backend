package com.memorin.domain.posts.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.posts.dto.request.PostSearchRequest;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.PostSortType;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.service.PostCursor;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostSearchRepository {

    private final EntityManager em;
    private final ObjectMapper objectMapper;

    // limit+1개를 가져와 호출부(PostService)가 hasNext를 판단한다.
    // findUserFeed/findFriendFeed와 동일한 관례 — 여기서 count 쿼리는 안 돌린다.
    public List<Post> search(UUID viewerId, PostSearchRequest condition, PostCursor.Cursor cursor, int limit) {
        WhereClause where = buildWhere(viewerId, condition, cursor);
        String orderBy = resolveOrderBy(condition);

        return em.createNativeQuery(
                "SELECT * FROM posts p WHERE " + where.sql() +
                    " ORDER BY " + orderBy +
                    " LIMIT :limit", Post.class)
            .unwrap(NativeQuery.class)
            .setProperties(where.params())
            .setParameter("limit", limit)
            .getResultList();
    }

    private WhereClause buildWhere(UUID viewerId, PostSearchRequest condition, PostCursor.Cursor cursor) {
        StringBuilder sql = new StringBuilder("p.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();

        // PostAccessPolicy.assertReadable과 동일한 판정: PUBLIC은 전체 공개, 본인 글은 visibility
        // 무관하게 항상 보임, FRIENDS는 양방향 ACCEPTED 팔로우가 있어야 보임.
        // 친구 판정 로직이 두 곳(단건 조회 vs 검색)에서 갈리면 #141이 재발하므로 반드시 맞춰야 한다.
        sql.append(" AND (" +
            "p.visibility = 'PUBLIC' " +
            "OR p.user_id = :viewerId " +
            "OR (p.visibility = 'FRIENDS' AND EXISTS (" +
            "    SELECT 1 FROM follows f " +
            "    WHERE ((f.follower_id = :viewerId AND f.following_id = p.user_id) " +
            "        OR (f.following_id = :viewerId AND f.follower_id = p.user_id)) " +
            "      AND f.status = 'ACCEPTED'" +
            "))" +
            ")");
        params.put("viewerId", viewerId);

        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            String normalized = condition.keyword().trim().toLowerCase();
            sql.append(" AND LOWER(p.content::text) LIKE :keywordPattern ESCAPE '\\'");
            params.put("keywordPattern", "%" + escapeLikePattern(normalized) + "%");
            params.put("keywordRaw", normalized);
        }

        boolean accuracySort = condition.sort() == PostSortType.ACCURACY_DESC;

        if (condition.tags() != null && !condition.tags().isEmpty()) {
            params.put("tags", writeTagNames(condition.tags()));
            if (accuracySort) {
                sql.append(" AND ").append(tagAccuracyExpr("p")).append(" > 0");
            } else {
                sql.append(" AND p.tags @> CAST(:tags AS jsonb)");
            }
        }

        if (condition.timeslot() != null) {
            sql.append(" AND p.timeslot = CAST(:timeslot AS timeslot_type)");
            params.put("timeslot", condition.timeslot().name());
        }

        if (cursor != null) {
            params.put("cursorId", UUID.fromString(cursor.postId()));
            params.put("cursorRecordedDate", cursor.recordedDate());
            sql.append(" AND ").append(keysetPredicate(condition));
        }

        return new WhereClause(sql.toString(), params);
    }

    // ORDER BY와 완전히 같은 튜플로 "커서 행보다 뒤에 오는 행"만 걸러낸다.
    // LATEST는 findUserFeed와 동일하게 :cursorRecordedDate를 직접 쓰고,
    // recorded_date가 정렬 기준이 아닌 VIEW_COUNT_DESC/ACCURACY_DESC는 커서 행(:cursorId)을
    // 서브쿼리로 다시 조회해 그 값과 비교한다 — PostCursor 페이로드를 바꾸지 않기 위함이다.
    private String keysetPredicate(PostSearchRequest condition) {
        PostSortType effective = condition.sort() != null ? condition.sort() : PostSortType.LATEST;
        return switch (effective) {
            case LATEST -> "(p.recorded_date, p.id) < (:cursorRecordedDate, :cursorId)";
            case VIEW_COUNT_DESC -> "(p.view_count, p.id) < " +
                "((SELECT view_count FROM posts WHERE id = :cursorId), :cursorId)";
            case ACCURACY_DESC -> "(" + accuracyScoreExpr(condition, "p") + ", p.recorded_date, p.id) < " +
                "((SELECT " + accuracyScoreExpr(condition, "p2") + " FROM posts p2 WHERE p2.id = :cursorId), " +
                " (SELECT recorded_date FROM posts WHERE id = :cursorId), :cursorId)";
        };
    }

    private String resolveOrderBy(PostSearchRequest condition) {
        PostSortType effective = condition.sort() != null ? condition.sort() : PostSortType.LATEST;
        return switch (effective) {
            case VIEW_COUNT_DESC -> "p.view_count DESC, p.id DESC";
            case ACCURACY_DESC -> accuracyScoreExpr(condition, "p") + " DESC, p.recorded_date DESC, p.id DESC";
            case LATEST -> "p.recorded_date DESC, p.id DESC";
        };
    }

    private String accuracyScoreExpr(PostSearchRequest condition, String alias) {
        boolean hasKeyword = condition.keyword() != null && !condition.keyword().isBlank();
        boolean hasTags = condition.tags() != null && !condition.tags().isEmpty();

        if (hasKeyword && hasTags) {
            return "(" + keywordAccuracyExpr(alias) + " + " + tagAccuracyExpr(alias) + ")";
        }
        if (hasKeyword) {
            return keywordAccuracyExpr(alias);
        }
        if (hasTags) {
            return tagAccuracyExpr(alias);
        }
        throw new IllegalStateException("정확도순 정렬은 keyword 또는 tags 중 하나가 필요합니다.");
    }

    private String keywordAccuracyExpr(String alias) {
        return "((LENGTH(LOWER(" + alias + ".content::text)) - LENGTH(REPLACE(LOWER(" + alias + ".content::text), :keywordRaw, ''))) " +
            "/ GREATEST(LENGTH(:keywordRaw), 1))";
    }

    private String tagAccuracyExpr(String alias) {
        return "(SELECT COUNT(*) FROM jsonb_array_elements_text(" + alias + ".tags) AS matched_tag " +
            " WHERE matched_tag IN (SELECT jsonb_array_elements_text(CAST(:tags AS jsonb))))";
    }

    private String escapeLikePattern(String raw) {
        return raw.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private String writeTagNames(List<TagType> tags) {
        try {
            return objectMapper.writeValueAsString(tags.stream().map(Enum::name).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("태그 직렬화 실패", e);
        }
    }

    private record WhereClause(String sql, Map<String, Object> params) {
    }
}
