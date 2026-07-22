package com.memorin.domain.posts.Service;

import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.posts.Repository.PostRepository;
import com.memorin.domain.posts.dto.Response.PostListResponse;
import com.memorin.domain.posts.dto.Response.PostSummaryResponse;
import com.memorin.domain.post_likes.Repository.PostLikeRepository;
import com.memorin.domain.post_comments.Repository.PostCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendedFeedService {

    private static final int CANDIDATE_POOL_SIZE = 300;
    private static final Duration RECENCY_WINDOW = Duration.ofDays(14);
    private static final double GRAVITY = 1.6;

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;

    public PostListResponse getRecommendedFeed(String cursor, Integer size) {
        int limit = normalizeSize(size);

        Instant asOf;
        Double cursorScore = null;
        UUID cursorPostId = null;

        if (cursor != null && !cursor.isBlank()) {
            RecommendFeedCursor.Cursor decoded = RecommendFeedCursor.decode(cursor);
            asOf = decoded.asOf(); // 첫 요청 때 고정된 기준 시각을 그대로 재사용
            cursorScore = decoded.score();
            cursorPostId = decoded.postId();
        } else {
            asOf = Instant.now(); // 이 피드 세션의 기준 시각을 여기서 딱 한 번 정한다
        }

        // 1. 후보 풀 조회 (posts 도메인 단독)
        List<Post> candidates = postRepository.findRecommendationCandidates(
                asOf.minus(RECENCY_WINDOW), CANDIDATE_POOL_SIZE
        );

        // 2. 좋아요/댓글 수 배치 조회 (도메인 경계를 넘지 않고 각 도메인의 배치 API 호출)
        List<UUID> candidateIds = candidates.stream().map(Post::getId).toList();
        Map<UUID, Long> likeCounts = postLikeRepository.countAllByPostIdIn(candidateIds);
        Map<UUID, Long> commentCounts = postCommentRepository.countAllByPostIdIn(candidateIds);

        // 3. 점수 계산 (자바에서)
        record Scored(Post post, double score, long likeCount, long commentCount) {}

        List<Scored> scoredList = candidates.stream()
                .map(p -> {
                    long likes = likeCounts.getOrDefault(p.getId(), 0L);
                    long comments = commentCounts.getOrDefault(p.getId(), 0L);
                    double score = computeScore(p, likes, comments, asOf);
                    return new Scored(p, score, likes, comments);
                })
                // 4. 정렬: score desc, tie-break postId desc
                .sorted(Comparator.<Scored>comparingDouble(Scored::score).reversed()
                        .thenComparing(s -> s.post().getId(), Comparator.reverseOrder()))
                .toList();

        // 5. 커서 이후 항목만 필터링 (자바 스트림에서 튜플 비교)
        Double finalCursorScore = cursorScore;
        UUID finalCursorPostId = cursorPostId;
        List<Scored> afterCursor = finalCursorScore == null
                ? scoredList
                : scoredList.stream()
                .filter(s -> isBefore(s.score(), s.post().getId(), finalCursorScore, finalCursorPostId))
                .toList();

        boolean hasNext = afterCursor.size() > limit;
        List<Scored> page = hasNext ? afterCursor.subList(0, limit) : afterCursor;

        List<PostSummaryResponse> items = page.stream()
                .map(s -> PostSummaryResponse.of(s.post(), List.of())) // 첨부파일 붙이려면 배치 조회 추가
                .toList();

        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Scored last = page.get(page.size() - 1);
            nextCursor = RecommendFeedCursor.encode(asOf, last.score(), last.post().getId());
        }

        return new PostListResponse(items, nextCursor, hasNext);
    }

    private double computeScore(Post post, long likeCount, long commentCount, Instant asOf) {
        double engagement = 1 + likeCount * 3 + commentCount * 2 + post.getViewCount() * 0.1;
        double hoursSinceCreated = Duration.between(post.getCreatedAt().toInstant(java.time.ZoneOffset.UTC), asOf).toMinutes() / 60.0;
        return Math.log(engagement) / Math.pow(hoursSinceCreated + 2, GRAVITY);
    }

    // (score, postId) < (cursorScore, cursorPostId) 를 score desc, postId desc 정렬 기준에 맞게 판별
    private boolean isBefore(double score, UUID postId, double cursorScore, UUID cursorPostId) {
        if (score != cursorScore) return score < cursorScore;
        return postId.compareTo(cursorPostId) < 0;
    }

    private int normalizeSize(Integer size) {
        if (size == null) return 20;
        return Math.min(Math.max(size, 1), 50);
    }
}