package com.memorin.domain.posts.controller;

import com.memorin.domain.posts.dto.request.PostSearchRequest;
import com.memorin.domain.posts.dto.response.PostSummaryResponse;
import com.memorin.domain.posts.entity.PostSortType;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.posts.service.RecommendedFeedService;
import com.memorin.domain.posts.dto.request.PostCreateRequest;
import com.memorin.domain.posts.dto.request.PostUpdateRequest;
import com.memorin.domain.posts.dto.response.PostCreateResponse;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostResponse;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Tag(name = "게시물", description = "게시물 작성 · 조회 · 수정 · 삭제 및 피드")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final RecommendedFeedService recommendedFeedService;

    @Operation(summary = "게시물 작성", description = "본문(content)과 첨부(attachments)로 게시물을 생성한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PostCreateResponse>> create(
            @RequestBody @Valid PostCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        PostCreateResponse response = postService.create(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "게시물 단건 조회", description = "postId로 게시물 하나를 조회한다. 공개범위 판정은 비로그인까지 지원하나, 현재 SecurityConfig가 모든 API에 인증을 요구하므로 토큰 없이 호출하면 401이다.")
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPostOne(
            @PathVariable("postId") UUID postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        UUID requesterId = userDetails != null ? userDetails.getUserId() : null;
        PostResponse response = postService.getOne(postId, requesterId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 게시물 목록(피드) 조회
    // GET /api/posts?userId={uuid}&cursor={cursor}&size=20&from=2026-08-01&to=2026-08-31
    // 쿼리 파라미터는 @RequestParam으로 받는다. @GetMapping 값에 적으면 경로 패턴으로 해석돼 매핑되지 않는다.
    @Operation(
            summary = "게시물 피드 조회",
            description = """
                    userId·cursor·size로 게시물 목록을 커서 페이지네이션으로 조회한다.
                    from/to(yyyy-MM-dd)를 주면 recorded_date 기준으로 기간을 좁힌다 — 캘린더 뷰용.
                    특정 하루만 보려면 from과 to에 같은 날짜를 준다(예: from=2026-08-11&to=2026-08-11).
                    범위 필터는 커서 페이지네이션과 함께 동작한다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PostListResponse>> getPostList(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @Parameter(description = "조회 시작일(포함), yyyy-MM-dd", example = "2026-08-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일(포함), yyyy-MM-dd", example = "2026-08-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ){
        UUID requesterId = userDetails != null ? userDetails.getUserId() : null;
        PostListResponse response = postService.list(userId, requesterId, cursor, size, from, to);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "게시물 수정", description = "작성자 본인만 수정할 수 있다.")
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> update(
            @PathVariable("postId") UUID postId,
            @RequestBody @Valid PostUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        PostResponse response = postService.update(postId, userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 게시물 삭제 (소프트 삭제)
    @Operation(summary = "게시물 삭제", description = "소프트 삭제(deleted_at 기록). 작성자 본인만 삭제할 수 있다.")
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("postId") UUID postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        postService.delete(postId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 친구 게시물 피드 조회
    @Operation(summary = "친구 피드 조회", description = "내가 팔로우한(ACCEPTED) 사용자들의 게시물만 커서 페이지네이션으로 조회한다.")
    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<PostListResponse>> getFriendFeed(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size,
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        PostListResponse response = postService.friendFeed(
                userDetails.getUserId(),
                cursor,
                size
            );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "추천 피드 조회",
        description = """
            최근 14일 내 전체공개 게시물 중 참여도와 최신성으로 점수를 매겨 정렬한다.
            cursor는 첫 요청의 기준 시각까지 함께 담고 있어, 페이지를 넘기는 동안 새 글이 올라와도
            목록이 밀리거나 중복되지 않는다. 직전 응답의 nextCursor를 그대로 넣는다.""")
    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<PostListResponse>> getRecommendedFeed(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recommendedFeedService.getRecommendedFeed(cursor, size)));
    }

    @Operation(summary = "게시물 필터링 검색",
        description = """
            게시물 전체 중에서 태그와 커스텀 메타데이터를 가진 게시물을 검색 UI에서 선택하여 검색할 수 있음.
            태그는 한번에 최대 3개까지 선택 가능하며, 이는 게시물을 올리는 상황에서도 동일함.""")
    @GetMapping("/search")
    public Page<PostSummaryResponse> search(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) List<TagType> tags,
        @RequestParam(required = false) TimeslotType timeslot,
        @RequestParam(required = false) PostSortType sort,
        @AuthenticationPrincipal UUID viewerId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        if (tags != null && tags.size() > 3) {
            throw new BusinessException(ErrorCode.POST_003, "태그는 최대 3개까지 선택할 수 있습니다.");
        }
        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        boolean hasTagsParam = tags != null && !tags.isEmpty();
        if (sort == PostSortType.ACCURACY_DESC && trimmedKeyword == null && !hasTagsParam) {
            throw new BusinessException(ErrorCode.POST_003, "정확도순 정렬은 검색어 또는 태그 중 하나는 필요합니다.");
        }
        PostSearchRequest condition = new PostSearchRequest(trimmedKeyword, tags, timeslot, sort);
        return postService.search(viewerId, condition, pageable);
    }

}
