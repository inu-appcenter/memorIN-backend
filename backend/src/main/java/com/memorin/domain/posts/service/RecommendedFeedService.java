package com.memorin.domain.posts.service;

import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.repository.PostRepository;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostMediaResponse;
import com.memorin.domain.posts.dto.response.PostSummaryResponse;
import com.memorin.domain.post_comments.repository.PostCommentRepository;
import com.memorin.domain.post_likes.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendedFeedService {

    private static final int CANDIDATE_POOL_SIZE = 300;
    private static final Duration RECENCY_WINDOW = Duration.ofDays(14);
    private static final double GRAVITY = 1.6;

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostMediaAttacher postMediaAttacher;

    public PostListResponse getRecommendedFeed(String cursor, Integer size) {
        int limit = normalizeSize(size);

        LocalDateTime asOf;
        Double cursorScore = null;
        UUID cursorPostId = null;

        if (cursor != null && !cursor.isBlank()) {
            RecommendFeedCursor.Cursor decoded = RecommendFeedCursor.decode(cursor);
            asOf = decoded.asOf(); // 첫 요청 때 고정된 기준 시각을 그대로 재사용
            cursorScore = decoded.score();
            cursorPostId = decoded.postId();
        } else {
            asOf = LocalDateTime.now(); // 이 피드 세션의 기준 시각을 여기서 딱 한 번 정한다
        }

        // 1. 후보 풀 조회 (posts 도메인 단독)
        List<Post> candidates = postRepository.findRecommendationCandidates(
                asOf.minus(RECENCY_WINDOW), asOf, CANDIDATE_POOL_SIZE
        );

        // 2. 댓글 수 · 좋아요 수 배치 조회 (도메인 경계를 넘지 않고 각 도메인의 배치 API 호출)
        List<UUID> candidateIds = candidates.stream().map(Post::getId).toList();
        Map<UUID, Long> commentCounts = postCommentRepository.countAllByPostIdIn(candidateIds, asOf);
        Map<UUID, Long> likeCounts = postLikeRepository.countAllByPostIdIn(candidateIds, asOf);

        // 3. 점수 계산 (자바에서)
        record Scored(Post post, double score, long commentCount) {}

        List<Scored> scoredList = candidates.stream()
                .map(p -> {
                    long comments = commentCounts.getOrDefault(p.getId(), 0L);
                    long likes = likeCounts.getOrDefault(p.getId(), 0L);
                    return new Scored(p, computeScore(p, comments, likes, asOf), comments);
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

        // 6. 첨부 미디어는 한 번의 IN 조회로 붙인다. 게시물마다 조회하면 그게 N+1이다.
        Map<UUID, List<PostMediaResponse>> mediaByPostId =
                postMediaAttacher.byPostId(page.stream().map(s -> s.post().getId()).toList());

        List<PostSummaryResponse> items = page.stream()
                .map(s -> PostSummaryResponse.of(
                        s.post(),
                        mediaByPostId.getOrDefault(s.post().getId(), List.of())))
                .toList();

        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Scored last = page.get(page.size() - 1);
            nextCursor = RecommendFeedCursor.encode(asOf, last.score(), last.post().getId());
        }

        return new PostListResponse(items, nextCursor, hasNext);
    }

    // #182에서 게시물 좋아요를 되살리며 likeCount * 3 항도 함께 복구했다.
    private double computeScore(Post post, long commentCount, long likeCount, LocalDateTime asOf) {
        double engagement = 1 + likeCount * 3 + commentCount * 2 + post.getViewCount() * 0.1;
        double hoursSinceCreated = Duration.between(post.getCreatedAt(), asOf).toMinutes() / 60.0;
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
