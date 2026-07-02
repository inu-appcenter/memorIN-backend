package com.memorin;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

// STOMP 에코 테스트: /pub/chat/room/{roomId}로 받은 메시지를
// /sub/chat/room/{roomId} 구독자에게 그대로 브로드캐스트한다.
// 실제 채팅 도메인(방, 메시지 영속화)은 DB 스키마 확정 후 설계한다.
@Controller
public class ChatController {

    @MessageMapping("/chat/room/{roomId}")
    @SendTo("/sub/chat/room/{roomId}")
    public ChatMessage handleMessage(@DestinationVariable Long roomId, ChatMessage message) {
        return message;
    }
}
