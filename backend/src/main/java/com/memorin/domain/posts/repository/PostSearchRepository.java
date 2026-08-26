package com.memorin.domain.posts.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.posts.dto.request.PostSearchRequest;
import com.memorin.domain.posts.entity.Post;
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

/**
 * 게시물 탐색(태그 + 메타데이터 필터링) 전용 리포지토리.
 *
 * jsonb 컨테인먼트(@>) 연산자를 써야 해서 JPQL로는 표현이 안 되고, 네이티브 쿼리로
 * 조건을 동적으로 조립한다. count 쿼리와 data 쿼리가 같은 WHERE 절을 공유해야
 * 페이지네이션 결과가 어긋나지 않으므로, 조건 조립은 buildWhere() 한 곳에서만 한다.
 */
@Repository
@RequiredArgsConstructor
public class PostSearchRepository {

    private final EntityManager em;
    private final ObjectMapper objectMapper;

    public Page<Post> search(UUID viewerId, PostSearchRequest condition, Pageable pageable) {
        WhereClause where = buildWhere(viewerId, condition);

        List<Post> content = em.createNativeQuery(
                "SELECT * FROM posts p WHERE " + where.sql() +
                    " ORDER BY p.recorded_date DESC, p.created_at DESC LIMIT :limit OFFSET :offset",
                Post.class)
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

        // 항상 적용 — 검색 조건과 무관하게 열람 권한 범위 밖 게시물은 절대 나오면 안 된다.
        sql.append(" AND (p.visibility = 'PUBLIC' OR p.user_id = :viewerId)");
        params.put("viewerId", viewerId);

        if (condition.tags() != null && !condition.tags().isEmpty()) {
            sql.append(" AND p.tags @> CAST(:tags AS jsonb)");
            params.put("tags", writeTagNames(condition.tags()));
        }
        if (condition.timeslot() != null) {
            sql.append(" AND p.timeslot = CAST(:timeslot AS timeslot_type)");
            params.put("timeslot", condition.timeslot().name());
        }
        if (condition.viewCountMin() != null) {
            sql.append(" AND p.view_count >= :viewCountMin");
            params.put("viewCountMin", condition.viewCountMin());
        }
        if (condition.viewCountMax() != null) {
            sql.append(" AND p.view_count <= :viewCountMax");
            params.put("viewCountMax", condition.viewCountMax());
        }
        if (condition.recordedDateFrom() != null) {
            sql.append(" AND p.recorded_date >= :dateFrom");
            params.put("dateFrom", condition.recordedDateFrom());
        }
        if (condition.recordedDateTo() != null) {
            sql.append(" AND p.recorded_date <= :dateTo");
            params.put("dateTo", condition.recordedDateTo());
        }

        return new WhereClause(sql.toString(), params);
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
