package com.memorin.domain.post_likes.controller;

import com.memorin.domain.post_likes.dto.response.PostLikeResponse;
import com.memorin.domain.post_likes.service.PostLikeService;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "게시물 좋아요", description = "게시물 좋아요 토글 및 집계 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts/{postId}/likes")
public class PostLikeController {

    private final PostLikeService postLikeService;

    @Operation(
        summary = "게시물 좋아요 토글",
        description = """
            이미 눌렀으면 취소, 안 눌렀으면 등록한다. 응답의 liked가 토글 후 최종 상태다.
            취소는 게시물 접근 권한과 무관하게 항상 가능하다(예: 팔로우 해제 후에도 과거에 누른 좋아요는 뗄 수 있다).
            동시 더블탭은 멱등 처리한다(liked=true 유지).""")
    @PostMapping
    public ResponseEntity<ApiResponse<PostLikeResponse>> toggle(
        @PathVariable UUID postId,
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        boolean liked = postLikeService.toggleLike(postId, userDetails.getUserId());
        long likeCount = postLikeService.countLikes(postId);
        return ResponseEntity.ok(ApiResponse.ok(new PostLikeResponse(liked, likeCount)));
    }

    @Operation(
        summary = "게시물 좋아요 집계 조회",
        description = "해당 게시물의 좋아요 수와 내가 눌렀는지 여부(liked)를 반환한다. 접근 권한이 없으면 POST_002.")
    @GetMapping
    public ResponseEntity<ApiResponse<PostLikeResponse>> getStatus(
        @PathVariable UUID postId,
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        UUID userId = userDetails.getUserId();
        postLikeService.assertReadableForLikes(postId, userId);
        boolean liked = postLikeService.isLikedBy(postId, userId);
        long likeCount = postLikeService.countLikes(postId);
        return ResponseEntity.ok(ApiResponse.ok(new PostLikeResponse(liked, likeCount)));
    }
}
