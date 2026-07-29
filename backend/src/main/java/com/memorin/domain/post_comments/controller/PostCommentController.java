package com.memorin.domain.post_comments.controller;

import com.memorin.domain.post_comments.dto.request.PostCommentCreateRequest;
import com.memorin.domain.post_comments.dto.request.PostCommentUpdateRequest;
import com.memorin.domain.post_comments.dto.response.PostCommentResponse;
import com.memorin.domain.post_comments.service.PostCommentService;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService postCommentService;

    // 생성/목록은 게시물에 종속되니 중첩 경로로, 수정/삭제는 댓글 자체가 대상이라 평평한 경로로 뒀다.
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<PostCommentResponse>> create(
        @PathVariable UUID postId,
        @RequestBody @Valid PostCommentCreateRequest request,
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        PostCommentResponse response = postCommentService.create(
            postId, userDetails.getUserId(), request.parentId(), request.body()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<PostCommentResponse>>> getThread(
        @PathVariable UUID postId,
        @AuthenticationPrincipal UserDetailsImpl userDetails // 비로그인 접근 허용 시 null
    ) {
        UUID requesterId = userDetails != null ? userDetails.getUserId() : null;
        return ResponseEntity.ok(ApiResponse.ok(postCommentService.getThread(postId, requesterId)));
    }

    @PatchMapping("/api/comments/{commentId}")
    public ResponseEntity<ApiResponse<PostCommentResponse>> update(
        @PathVariable UUID commentId,
        @RequestBody @Valid PostCommentUpdateRequest request,
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        PostCommentResponse response = postCommentService.update(commentId, userDetails.getUserId(), request.body());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable UUID commentId,
        @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        postCommentService.delete(commentId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
