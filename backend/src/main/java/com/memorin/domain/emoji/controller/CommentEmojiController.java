package com.memorin.domain.emoji.controller;

import com.memorin.domain.emoji.dto.request.EmojiRequest;
import com.memorin.domain.emoji.dto.response.EmojiSummary;
import com.memorin.domain.emoji.dto.response.EmojiToggleResponse;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.emoji.service.CommentEmojiService;
import com.memorin.global.exception.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments/{commentId}/emojis")
public class CommentEmojiController {

    private final CommentEmojiService commentEmojiService;

    @PostMapping
    public ResponseEntity<EmojiToggleResponse> toggle(
        @PathVariable UUID commentId,
        @RequestBody @Valid EmojiRequest request,
        @AuthenticationPrincipal UserDetailsImpl userDetails) {


        return ResponseEntity.ok(
            commentEmojiService.toggle(userDetails.getUserId(), commentId, request.emojiType()));
    }

    @DeleteMapping("/{emojiType}")
    public ResponseEntity<Void> remove(
        @PathVariable UUID commentId,
        @PathVariable EmojiType emojiType,
        @AuthenticationPrincipal UserDetailsImpl userDetails) {
        commentEmojiService.remove(userDetails.getUserId(), commentId, emojiType);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<EmojiSummary>> getEmojis(
        @PathVariable UUID commentId,
        @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(
            commentEmojiService.getEmojis(commentId, userDetails.getUserId()));
    }
}
