package com.memorin;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;

    public ChatController(ChatRoomRepository chatRoomRepository,
                          MessageRepository messageRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.messageRepository = messageRepository;
    }

    @MessageMapping("/chat/room/{roomId}")
    @SendTo("/sub/chat/room/{roomId}")
    public ChatMessage handleMessage(@DestinationVariable Long roomId, ChatMessage message) {
        chatRoomRepository.findById(roomId).ifPresent(room -> {
            messageRepository.save(new Message(message.sender(), message.content(), room));
        });

        return message;
    }
}
