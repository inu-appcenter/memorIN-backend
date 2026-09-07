package com.memorin.domain.messages.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_rooms.repository.ChatRoomsRepository;
import com.memorin.domain.messages.content.MessageContent;
import com.memorin.domain.messages.content.PostShareContent;
import com.memorin.domain.messages.content.TextContent;
import com.memorin.domain.messages.dto.response.MessageResponse;
import com.memorin.domain.messages.dto.response.MessagePageResponse;
import com.memorin.domain.messages.dto.request.PostShareRequest;
import com.memorin.domain.messages.dto.request.TextRequest;
import com.memorin.domain.messages.entity.Messages;
import com.memorin.domain.messages.repository.MessageRepository;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.repository.PostRepository;
import com.memorin.domain.posts.service.PostAccessPolicy;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final MessageRepository messagesRepository;
    private final ChatRoomsRepository chatRoomsRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostAccessPolicy postAccessPolicy;
    private final ObjectMapper objectMapper;

    // 게시물 공유 메세지 생성
    @Transactional
    public MessageResponse sharePost(UUID senderId, PostShareRequest request) {
        ChatRooms room = (ChatRooms) chatRoomsRepository.findById(request.roomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "채팅방이 존재하지 않습니다: "));

        if (!chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(request.roomId(), senderId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_MEMBERS_001, "존재하지 않는 참여자입니다: ");
        }

        Post post = postRepository.findById(request.postId())
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_001, "존재하지 않는 게시물입니다: "));

        if(post.isDeleted()){
            throw new BusinessException(ErrorCode.POST_001, "삭제된 게시물은 공유할 수 없습니다.");
        }

        postAccessPolicy.assertReadable(post, senderId);

        User sender = userRepository.getReferenceById(senderId);

        PostShareContent shareContent = new PostShareContent(post.getId());
        Messages message = Messages.createPostShare(room, sender, writeJson(shareContent));
        messagesRepository.save(message);

        return MessageResponse.of(message, shareContent);
    }

    // 텍스트 메세지 생성
    @Transactional
    public MessageResponse sendText(UUID senderId, TextRequest request) {
        ChatRooms room = (ChatRooms) chatRoomsRepository.findById(request.roomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "채팅방이 존재하지 않습니다: "));

        if (!chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(request.roomId(), senderId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_MEMBERS_001, "존재하지 않는 참여자입니다: ");
        }

        User sender = userRepository.getReferenceById(senderId);

        TextContent textContent = new TextContent(request.text());
        Messages message = Messages.createText(room, sender, writeJson(textContent));
        messagesRepository.save(message);

        return MessageResponse.of(message, textContent);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(UUID userId, UUID roomId, UUID cursor, Integer size) {
        chatRoomsRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001));

        if (!chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_MEMBERS_001);
        }

        int limit = normalizeSize(size);
        Pageable pageable = PageRequest.of(0, limit + 1);
        List<Messages> messages = messagesRepository.findMessages(roomId, cursor, pageable);
        boolean hasNext = messages.size() == limit + 1;

        if (hasNext) {
            messages.remove(limit);
        }

        List<MessageResponse> items = new ArrayList<>();
        for (Messages message : messages) {
            items.add(MessageResponse.of(message, readContent(message.getContent())));
        }

        UUID nextCursor = hasNext ? messages.get(messages.size() - 1).getId() : null;
        return new MessagePageResponse(items, nextCursor, hasNext);
    }

    // 게시물 공유 메세지 Jsonb 형태로 변환
    private String writeJson(MessageContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("메시지 content 직렬화 실패", e);
        }
    }

    private MessageContent readContent(String contentJson) {
        try {
            return objectMapper.readValue(contentJson, MessageContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize message content", e);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
