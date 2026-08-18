package com.memorin.domain.messages.controller;

import com.memorin.domain.messages.dto.response.MessageResponse;
import com.memorin.domain.messages.dto.request.PostShareRequest;
import com.memorin.domain.messages.service.MessageService;
import com.memorin.global.exception.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sharePost")
    public void sharePost(@Payload PostShareRequest request,
                          @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UUID senderId = UUID.fromString(userDetails.getUsername());
        MessageResponse response = messageService.sharePost(senderId, request);
        messagingTemplate.convertAndSend("/topic/rooms/" + request.roomId(), response);
    }
}
