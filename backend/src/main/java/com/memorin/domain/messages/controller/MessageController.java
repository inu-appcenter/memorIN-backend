package com.memorin.domain.messages.controller;

import com.memorin.domain.messages.dto.response.MessageResponse;
import com.memorin.domain.messages.dto.response.MessagePageResponse;
import com.memorin.domain.messages.dto.request.PostShareRequest;
import com.memorin.domain.messages.dto.request.TextRequest;
import com.memorin.domain.messages.service.MessageService;
import com.memorin.global.common.ApiResponse;
import com.memorin.global.exception.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-rooms")
@Tag(name = "Chat messages", description = "Send and retrieve chat-room messages")
public class MessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/{roomId}/messages")
    @Operation(
        summary = "Get chat history",
        description = "Returns messages newest first. Pass nextCursor as cursor to load older messages."
    )
    public ApiResponse<MessagePageResponse> getMessages(
        @PathVariable UUID roomId,
        @AuthenticationPrincipal UserDetailsImpl userDetails,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(messageService.getMessages(userDetails.getUserId(), roomId, cursor, size));
    }

    @MessageMapping("/chat.sharePost")
    public void sharePost(@Payload PostShareRequest request,
                          @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UUID senderId = userDetails.getUserId();
        MessageResponse response = messageService.sharePost(senderId, request);
        messagingTemplate.convertAndSend("/topic/rooms/" + request.roomId(), response);
    }

    @MessageMapping("/chat.sendText")
    public void sendText(@Payload TextRequest request,
                         @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UUID senderId = userDetails.getUserId();
        MessageResponse response = messageService.sendText(senderId, request);
        messagingTemplate.convertAndSend("/topic/rooms/" + request.roomId(), response);
    }
}
