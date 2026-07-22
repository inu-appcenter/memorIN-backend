package com.memorin.domain.posts.Controller;

import com.memorin.domain.posts.Service.PostService;
import com.memorin.domain.posts.dto.Request.PostCreateRequest;
import com.memorin.domain.posts.dto.Request.PostUpdateRequest;
import com.memorin.domain.posts.dto.Response.PostCreateResponse;
import com.memorin.domain.posts.dto.Response.PostListResponse;
import com.memorin.domain.posts.dto.Response.PostResponse;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;


    // 새 Post 생성
    @PostMapping
    public ResponseEntity<ApiResponse<PostCreateResponse>> create(
            @RequestBody @Valid PostCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        PostCreateResponse response = postService.create(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // 게시물 단건 조회
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
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("postId") UUID postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        postService.delete(postId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }


}
