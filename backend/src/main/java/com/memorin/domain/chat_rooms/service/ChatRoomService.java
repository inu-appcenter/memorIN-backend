package com.memorin.domain.chat_rooms.service;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.dto.request.InviteMembersRequest;
import com.memorin.domain.chat_rooms.dto.request.RenameRoomRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomSummaryResponse;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_rooms.entity.Chat_type;
import com.memorin.domain.chat_rooms.repository.ChatRoomsRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomsRepository chatRoomsRepository;
    private final ChatRoomMemberRepository chatRoomMembersRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatRoomResponse createDirectRoom(UUID requesterId, UUID targetUserId) {
        if (requesterId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신과 1:1 채팅방을 만들 수 없습니다.");
        }

        Optional<UUID> existingRoomId = chatRoomMembersRepository.findActiveDirectRoomId(requesterId, targetUserId);
        if (existingRoomId.isPresent()) {
            ChatRooms room = chatRoomsRepository.findById(existingRoomId.get())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "존재하지 않는 채팅방입니다." + existingRoomId.get()));
            return new ChatRoomResponse(room.getId(), room.getType(), room.getName(), false);
        }

        User requester = userRepository.findById(requesterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "존재하지 않는 회원입니다." + requesterId));

        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "상대방을 찾을 수 없습니다." + targetUserId));

        ChatRooms room = chatRoomsRepository.save(ChatRooms.createDirect());
        chatRoomMembersRepository.save(ChatRoomMembers.ofOwner(room, requester));
        chatRoomMembersRepository.save(ChatRoomMembers.ofMember(room, target));

        return new ChatRoomResponse(room.getId(), room.getType(), room.getName(), true);
    }

    @Transactional
    public ChatRoomResponse createGroupRoom(UUID requesterId, CreateGroupRoomRequest request) {
        User requester = userRepository.findById(requesterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "존재하지 않는 회원입니다." + requesterId));

        ChatRooms room = chatRoomsRepository.save(ChatRooms.createGroup(request.name()));
        chatRoomMembersRepository.save(ChatRoomMembers.ofOwner(room, requester));

        for (UUID memberId : request.memberIds()) {
            if (memberId.equals(requesterId)) continue;
            User member = userRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "초대 대상을 찾을 수 없습니다." + memberId));
            chatRoomMembersRepository.save(ChatRoomMembers.ofMember(room, member));
        }

        return new ChatRoomResponse(room.getId(), room.getType(), room.getName(), true);
    }

    @Transactional
    public void inviteMembers(UUID roomId, UUID requesterId, InviteMembersRequest request) {
        ChatRooms room = getGroupRoomOrThrow(roomId);
        requireActiveMember(room, requesterId);

        for (UUID memberId : request.memberIds()) {
            addOrRejoinMember(room, memberId);
        }
    }

    private void addOrRejoinMember(ChatRooms room, UUID userId) {
        Optional<ChatRoomMembers> existing = chatRoomMembersRepository.findByRoom_IdAndUser_Id(room.getId(), userId);

        if (existing.isPresent()) {
            ChatRoomMembers membership = existing.get();
            if (!membership.isActive()) {
                membership.rejoin(); // uq_room_member 제약 때문에 새로 INSERT 불가 — 기존 행을 되살림
            }
            return;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "초대 대상을 찾을 수 없습니다." + userId));
        chatRoomMembersRepository.save(ChatRoomMembers.ofMember(room, user));
    }

    @Transactional
    public void kickMember(UUID roomId, UUID requesterId, UUID targetUserId) {
        if (requesterId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOMS_002, "자기 자신은 강퇴할 수 없습니다. 나기기를 이용해주세요");
        }

        ChatRooms room = getGroupRoomOrThrow(roomId);
        ChatRoomMembers requester = requireActiveMember(room, requesterId);
        if (!requester.isOwner()) {
            throw new BusinessException(ErrorCode.CHAT_ROOMS_002, "방장만 멤버를 강퇴할 수 있습니다.");
        }

        ChatRoomMembers target = chatRoomMembersRepository.findByRoom_IdAndUser_Id(roomId, targetUserId)
            .filter(ChatRoomMembers::isActive)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_MEMBERS_001, "채팅방의 멤버가 아닙니다." + targetUserId));

        target.leave();
    }

    @Transactional
    public void leaveRoom(UUID roomId, UUID requesterId) {
        ChatRooms room = chatRoomsRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "채팅방을 찾을 수 없습니다." + roomId));
        ChatRoomMembers member = requireActiveMember(room, requesterId);

        member.leave();

        if (member.isOwner() && room.getType() == Chat_type.GROUP) {
            chatRoomMembersRepository.findByRoom_IdAndLeftAtIsNull(roomId).stream()
                .min(Comparator.comparing(ChatRoomMembers::getJoinedAt))
                .ifPresent(ChatRoomMembers::promoteToOwner);
        }
    }

    @Transactional
    public void renameRoom(UUID roomId, UUID requesterId, RenameRoomRequest request) {
        ChatRooms room = getGroupRoomOrThrow(roomId);
        ChatRoomMembers requester = requireActiveMember(room, requesterId);
        if (!requester.isOwner()) {
            throw new BusinessException(ErrorCode.CHAT_ROOMS_002, "방장만 방의 이름을 변경할 수 있습니다.");
        }
        room.rename(request.name());
    }

    @Transactional(readOnly = true)
    public List<ChatRoomSummaryResponse> listMyRooms(UUID requesterId) {
        return chatRoomMembersRepository.findByUser_IdAndLeftAtIsNullOrderByJoinedAtDesc(requesterId).stream()
            .map(m -> new ChatRoomSummaryResponse(m.getRoom().getId(), m.getRoom().getType(), m.getRoom().getName(), m.getRole()))
            .toList();
    }

    private ChatRooms getGroupRoomOrThrow(UUID roomId) {
        ChatRooms room = chatRoomsRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "채팅방을 찾을 수 없습니다." + roomId));
        if (room.getType() != Chat_type.GROUP) {
            throw new IllegalStateException("1:1 채팅방에는 사용할 수 없는 기능입니다.");
        }
        return room;
    }

    private ChatRoomMembers requireActiveMember(ChatRooms room, UUID userId) {
        return chatRoomMembersRepository.findByRoom_IdAndUser_Id(room.getId(), userId)
            .filter(ChatRoomMembers::isActive)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_MEMBERS_001, "채팅방의 멤버가 아닙니다." + userId));
    }
}
