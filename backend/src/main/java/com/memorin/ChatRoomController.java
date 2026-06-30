package com.memorin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class ChatRoomController {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MessageRepository messageRepository;

    public ChatRoomController(ChatRoomRepository chatRoomRepository,
                              ChatRoomMemberRepository chatRoomMemberRepository,
                              MessageRepository messageRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.messageRepository = messageRepository;
    }

    // 채팅방 생성
    @PostMapping
    public ResponseEntity<ChatRoom> createRoom(@RequestBody String name) {
        ChatRoom room = chatRoomRepository.save(new ChatRoom(name));
        return ResponseEntity.ok(room);
    }

    // 채팅방 목록
    @GetMapping
    public ResponseEntity<Iterable<ChatRoom>> getRooms() {
        return ResponseEntity.ok(chatRoomRepository.findAll());
    }

    // 채팅방 참여 (참여자로 등록해야 알림을 받음)
    @PostMapping("/{roomId}/members")
    public ResponseEntity<Void> joinRoom(@PathVariable Long roomId, @RequestParam String userId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            chatRoomMemberRepository.save(new ChatRoomMember(room, userId));
        }
        return ResponseEntity.ok().build();
    }

    // 메시지 내역 조회 (커서 기반 페이징)
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = org.springframework.data.domain.PageRequest.of(0, size);
        List<Message> messages;

        if (cursor == null) {
            messages = messageRepository.findByChatRoomIdOrderByIdDesc(roomId, pageable);
        } else {
            messages = messageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor, pageable);
        }

        return ResponseEntity.ok(messages);
    }
}