package com.memorin.domain.emoji.controller;

import com.memorin.domain.emoji.dto.request.EmojiRequest;
import com.memorin.domain.emoji.dto.response.EmojiSummary;
import com.memorin.domain.emoji.dto.response.EmojiToggleResponse;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.emoji.service.CommentEmojiService;
import com.memorin.global.exception.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "댓글 이모지", description = "댓글 반응(이모지) 토글 · 삭제 · 집계 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments/{commentId}/emojis")
public class CommentEmojiController {

    private final CommentEmojiService commentEmojiService;

    @Operation(
        summary = "댓글 이모지 토글",
        description = """
            같은 이모지를 다시 누르면 취소된다. 응답의 added=true면 추가, false면 취소다.
            동시 더블클릭은 멱등 처리한다(added=true 유지). 삭제된 댓글에는 달 수 없다(COMMENT_EMOJI_001).
            응답이 공통 봉투(ApiResponse)가 아닌 DTO 직접 반환인 점에 주의한다.""")
    @PostMapping
    public ResponseEntity<EmojiToggleResponse> toggle(
        @PathVariable UUID commentId,
        @RequestBody @Valid EmojiRequest request,
        @AuthenticationPrincipal UserDetailsImpl userDetails) {

        return ResponseEntity.ok(
            commentEmojiService.toggle(userDetails.getUserId(), commentId, request.emojiType()));
    }

    @Operation(
        summary = "댓글 이모지 삭제",
        description = "내가 단 이모지를 명시적으로 제거한다. 달지 않은 상태여도 204를 반환한다(멱등).")
    @DeleteMapping("/{emojiType}")
    public ResponseEntity<Void> remove(
        @PathVariable UUID commentId,
        @PathVariable EmojiType emojiType,
        @AuthenticationPrincipal UserDetailsImpl userDetails) {
        commentEmojiService.remove(userDetails.getUserId(), commentId, emojiType);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "댓글 이모지 집계 조회",
        description = """
            해당 댓글의 이모지별 개수와 내가 눌렀는지 여부(reactedByMe)를 반환한다.
            댓글 스레드 조회(GET /api/posts/{postId}/comments)에 이미 같은 집계가 포함되므로,
            스레드를 그리는 화면에서는 이 API를 추가로 호출하지 않아도 된다.""")
    @GetMapping
    public ResponseEntity<List<EmojiSummary>> getEmojis(
        @PathVariable UUID commentId,
        @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(
            commentEmojiService.getEmojis(commentId, userDetails.getUserId()));
    }
}
