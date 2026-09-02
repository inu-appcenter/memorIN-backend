package com.memorin.domain.posts.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.posts.dto.request.PostSearchRequest;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.PostSortType;
import com.memorin.domain.posts.entity.TagType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostSearchRepository {

    // "정확도"를 구성하는 두 부분 점수. 어느 필터가 실제로 걸려 있는지에 따라
    // 둘 중 하나만 쓰거나 더해서 쓴다 (resolveAccuracyOrderBy 참고).
    //
    // 키워드 등장 빈도: LENGTH(content) - LENGTH(REPLACE(content, keyword, '')) 는
    // 제거된 문자 수(= 등장 횟수 * 키워드 길이)이므로 키워드 길이로 나누면 등장 횟수가 된다.
    private static final String KEYWORD_ACCURACY_EXPR =
        "((LENGTH(LOWER(p.content::text)) - LENGTH(REPLACE(LOWER(p.content::text), :keywordRaw, ''))) " +
            "/ GREATEST(LENGTH(:keywordRaw), 1))";

    // 태그 일치 개수: 요청한 태그와 게시물 태그의 교집합 크기.
    private static final String TAG_ACCURACY_EXPR =
        "(SELECT COUNT(*) FROM jsonb_array_elements_text(p.tags) AS matched_tag " +
            " WHERE matched_tag IN (SELECT jsonb_array_elements_text(CAST(:tags AS jsonb))))";

    private final EntityManager em;
    private final ObjectMapper objectMapper;

    public Page<Post> search(UUID viewerId, PostSearchRequest condition, Pageable pageable) {
        WhereClause where = buildWhere(viewerId, condition);
        String orderBy = resolveOrderBy(condition);

        List<Post> content = em.createNativeQuery(
                "SELECT * FROM posts p WHERE " + where.sql() +
                    " ORDER BY " + orderBy +
                    " LIMIT :limit OFFSET :offset", Post.class)
            .unwrap(NativeQuery.class)
            .setProperties(where.params())
            .setParameter("limit", pageable.getPageSize())
            .setParameter("offset", pageable.getOffset())
            .getResultList();

        long total = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM posts p WHERE " + where.sql())
            .unwrap(NativeQuery.class)
            .setProperties(where.params())
            .getSingleResult()).longValue();

        return new PageImpl<>(content, pageable, total);
    }

    private WhereClause buildWhere(UUID viewerId, PostSearchRequest condition) {
        StringBuilder sql = new StringBuilder("p.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();

        sql.append(" AND (p.visibility = 'PUBLIC' OR p.user_id = :viewerId)");
        params.put("viewerId", viewerId);

        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            String normalized = condition.keyword().trim().toLowerCase();
            sql.append(" AND LOWER(p.content::text) LIKE :keywordPattern ESCAPE '\\'");
            params.put("keywordPattern", "%" + escapeLikePattern(normalized) + "%");
            params.put("keywordRaw", normalized);
        }

        if (condition.tags() != null && !condition.tags().isEmpty()) {
            params.put("tags", writeTagNames(condition.tags()));
            boolean accuracySort = condition.sort() == PostSortType.ACCURACY_DESC;
            if (accuracySort) {
                // 정확도순일 때는 "전부 일치"가 아니라 "하나라도 겹치면 후보"로 넓게 잡는다.
                // 그래야 겹치는 개수(TAG_ACCURACY_EXPR)가 행마다 달라져서 정렬 기준으로 의미가 생긴다.
                // 전부 일치(AND)로 좁히면 통과한 행은 전부 겹침 개수가 동일해서 정렬이 무의미해진다.
                sql.append(" AND ").append(TAG_ACCURACY_EXPR).append(" > 0");
            } else {
                // 일반 태그 필터는 요청한 태그를 전부 가진 게시물만 AND로 매칭한다 (기존 동작 유지).
                sql.append(" AND p.tags @> CAST(:tags AS jsonb)");
            }
        }

        if (condition.timeslot() != null) {
            sql.append(" AND p.timeslot = CAST(:timeslot AS timeslot_type)");
            params.put("timeslot", condition.timeslot().name());
        }

        return new WhereClause(sql.toString(), params);
    }

    // sort는 닫힌 enum이라 여기서 고정된 SQL 리터럴로만 매핑한다.
    // ORDER BY 절의 컬럼/표현식은 JDBC 바인드 파라미터로 넘길 수 없어서(":sort" 같은 걸 못 씀),
    // 이 메서드가 사용자 입력이 SQL 텍스트에 닿는 유일한 지점이다 — 그래서 반드시
    // 고정 문자열 조합만 반환하고, 사용자 값을 여기서 절대 문자열로 잇지 않는다.
    private String resolveOrderBy(PostSearchRequest condition) {
        PostSortType effective = condition.sort() != null ? condition.sort() : PostSortType.LATEST;
        return switch (effective) {
            // id는 UUIDv7이라 생성 순서와 거의 일치 — 동점 상황에서 안정적인 2차 정렬 기준으로 적합
            case VIEW_COUNT_DESC -> "p.view_count DESC, p.id DESC";
            case ACCURACY_DESC -> resolveAccuracyOrderBy(condition);
            case LATEST -> "p.recorded_date DESC, p.created_at DESC, p.id DESC";
        };
    }

    // ACCURACY_DESC는 keyword/tags 중 최소 하나가 바인딩되어 있어야 동작한다 — 그 불변식은
    // 서비스 계층(PostSearchService)에서 사전 검증한다. 여기서는 그 불변식을 전제로,
    // 실제로 걸려 있는 필터에 맞는 점수식만 골라 쓴다.
    private String resolveAccuracyOrderBy(PostSearchRequest condition) {
        boolean hasKeyword = condition.keyword() != null && !condition.keyword().isBlank();
        boolean hasTags = condition.tags() != null && !condition.tags().isEmpty();

        String scoreExpr;
        if (hasKeyword && hasTags) {
            scoreExpr = "(" + KEYWORD_ACCURACY_EXPR + " + " + TAG_ACCURACY_EXPR + ")";
        } else if (hasKeyword) {
            scoreExpr = KEYWORD_ACCURACY_EXPR;
        } else if (hasTags) {
            scoreExpr = TAG_ACCURACY_EXPR;
        } else {
            // 서비스 계층 검증을 우회해서 여기까지 온 경우 — 방어적으로 막는다.
            throw new IllegalStateException("정확도순 정렬은 keyword 또는 tags 중 하나가 필요합니다.");
        }
        return scoreExpr + " DESC, p.recorded_date DESC, p.id DESC";
    }

    // LIKE 패턴에서 %, _, \ 는 와일드카드로 해석되므로, 검색어에 이 문자가 그대로 들어있으면
    // 이스케이프해서 리터럴로 취급한다.
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
