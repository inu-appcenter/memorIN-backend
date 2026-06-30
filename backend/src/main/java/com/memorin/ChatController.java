package com.memorin;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class ChatController {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MessageRepository messageRepository;
    private final FcmService fcmService;

    public ChatController(ChatRoomRepository chatRoomRepository,
                          ChatRoomMemberRepository chatRoomMemberRepository,
                          MessageRepository messageRepository,
                          FcmService fcmService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.messageRepository = messageRepository;
        this.fcmService = fcmService;
    }

    @MessageMapping("/chat/room/{roomId}")
    @SendTo("/sub/chat/room/{roomId}")
    public ChatMessage handleMessage(@DestinationVariable Long roomId, ChatMessage message) {
        chatRoomRepository.findById(roomId).ifPresent(room -> {
            messageRepository.save(new Message(message.sender(), message.content(), room));
        });

        // 방 참여자 중 '보낸 사람'을 제외한 나머지에게만 푸시 알림
        List<String> recipients = chatRoomMemberRepository.findByChatRoomId(roomId).stream()
                .map(ChatRoomMember::getUserId)
                .filter(userId -> !userId.equals(message.sender()))
                .toList();

        if (!recipients.isEmpty()) {
            fcmService.sendToUsers(recipients, message.sender(), message.content());
        }

        return message;
    }
}
