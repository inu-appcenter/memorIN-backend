package com.memorin.domain.posts.controller;

import com.memorin.domain.posts.service.PostService;
import com.memorin.domain.posts.dto.request.PostCreateRequest;
import com.memorin.domain.posts.dto.request.PostUpdateRequest;
import com.memorin.domain.posts.dto.response.PostCreateResponse;
import com.memorin.domain.posts.dto.response.PostListResponse;
import com.memorin.domain.posts.dto.response.PostResponse;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "게시물", description = "게시물 작성 · 조회 · 수정 · 삭제 및 피드")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;


    // 새 Post 생성
    @Operation(summary = "게시물 작성", description = "본문(content)과 첨부(attachments)로 게시물을 생성한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PostCreateResponse>> create(
            @RequestBody @Valid PostCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        PostCreateResponse response = postService.create(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // 게시물 단건 조회
    @Operation(summary = "게시물 단건 조회", description = "postId로 게시물 하나를 조회한다. 비로그인도 공개 게시물은 조회 가능.")
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
    // GET /api/posts?userId={uuid}&cursor={cursor}&size=20
    // 쿼리 파라미터는 @RequestParam으로 받는다. @GetMapping 값에 적으면 경로 패턴으로 해석돼 매핑되지 않는다.
    @Operation(summary = "게시물 피드 조회", description = "userId·cursor·size로 게시물 목록을 커서 페이지네이션으로 조회한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PostListResponse>> getPostList(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ){
        UUID requesterId = userDetails != null ? userDetails.getUserId() : null;
        PostListResponse response = postService.list(userId, requesterId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 게시물 수정
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


}
