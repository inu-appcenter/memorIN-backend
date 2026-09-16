package com.memorin.domain.chat_rooms.service;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.chat_room_members.repository.ChatRoomMemberRepository;
import com.memorin.domain.chat_room_members.repository.projection.RoomActivityProjection;
import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.dto.request.InviteMembersRequest;
import com.memorin.domain.chat_rooms.dto.request.RenameRoomRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomSummaryResponse;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_rooms.entity.Chat_type;
import com.memorin.domain.chat_rooms.event.ChatRoomMembershipChanged;
import com.memorin.domain.chat_rooms.repository.ChatRoomsRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomsRepository chatRoomsRepository;
    private final ChatRoomMemberRepository chatRoomMembersRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatRoomResponse createDirectRoom(UUID requesterId, UUID targetUserId) {
        if (requesterId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOMS_002, "자기 자신과 1:1 채팅방을 만들 수 없습니다.");
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

        for (UUID memberId : distinct(request.memberIds())) {
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

        for (UUID memberId : distinct(request.memberIds())) {
            addOrRejoinMember(room, memberId);
        }
    }

    // 같은 요청에 중복 UUID가 들어오면 uq_room_member 제약 위반으로 500이 난다.
    // 거절하지 않고 걸러낸다 — [A, A, B]를 보낸 클라이언트의 의도는 "A와 B를 초대"이지
    // 오류를 보고 싶은 것이 아니다. 결과도 중복을 건 쪽과 같다.
    // 순서는 유지한다(LinkedHashSet). 초대 실패 시 어느 대상에서 멈췄는지 재현 가능해야 한다.
    private static List<UUID> distinct(List<UUID> memberIds) {
        return List.copyOf(new LinkedHashSet<>(memberIds));
    }

    private void addOrRejoinMember(ChatRooms room, UUID userId) {
        Optional<ChatRoomMembers> existing = chatRoomMembersRepository.findByRoom_IdAndUser_Id(room.getId(), userId);

        if (existing.isPresent()) {
            ChatRoomMembers membership = existing.get();
            if (!membership.isActive()) {
                membership.rejoin(); // uq_room_member 제약 때문에 새로 INSERT 불가 — 기존 행을 되살림
                membershipChanged(userId, room.getId());
            }
            return;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "초대 대상을 찾을 수 없습니다." + userId));
        chatRoomMembersRepository.save(ChatRoomMembers.ofMember(room, user));
        membershipChanged(userId, room.getId());
    }

    @Transactional
    public void kickMember(UUID roomId, UUID requesterId, UUID targetUserId) {
        if (requesterId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOMS_002, "자기 자신은 강퇴할 수 없습니다. 나가기를 이용해주세요.");
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
        membershipChanged(targetUserId, roomId);
    }

    @Transactional
    public void leaveRoom(UUID roomId, UUID requesterId) {
        ChatRooms room = chatRoomsRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "채팅방을 찾을 수 없습니다." + roomId));
        ChatRoomMembers member = requireActiveMember(room, requesterId);

        member.leave();
        membershipChanged(requesterId, roomId);

        if (member.isOwner() && room.getType() == Chat_type.GROUP) {
            chatRoomMembersRepository.findByRoom_IdAndLeftAtIsNull(roomId).stream()
                .min(Comparator.comparing(ChatRoomMembers::getJoinedAt))
                .ifPresent(ChatRoomMembers::promoteToOwner);
        }
    }

    // DIRECT·GROUP 둘 다 대상이라 getGroupRoomOrThrow가 아니라 leaveRoom과 같은 방식으로 조회한다.
    @Transactional
    public void markAsRead(UUID roomId, UUID requesterId) {
        ChatRooms room = chatRoomsRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "채팅방을 찾을 수 없습니다." + roomId));
        ChatRoomMembers member = requireActiveMember(room, requesterId);

        member.updateLastRead();
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
        List<ChatRoomMembers> memberships = chatRoomMembersRepository.findByUser_IdAndLeftAtIsNullOrderByJoinedAtDesc(requesterId);

        // memberships와 동일한 WHERE 조건(user_id, left_at IS NULL)으로 같은 트랜잭션에서 조회하므로
        // 방 id 집합이 완전히 겹친다 — 방 개수와 무관하게 이 한 쿼리로 unreadCount·lastMessage를 채운다.
        Map<UUID, RoomActivityProjection> activityByRoomId = chatRoomMembersRepository.findRoomActivityByUserId(requesterId).stream()
            .collect(Collectors.toMap(RoomActivityProjection::getRoomId, Function.identity()));

        return memberships.stream()
            .map(m -> ChatRoomSummaryResponse.of(
                m.getRoom().getId(), m.getRoom().getType(), m.getRoom().getName(), m.getRole(),
                activityByRoomId.get(m.getRoom().getId())))
            .toList();
    }

    // 멤버십이 바뀌면 알린다. 리스너가 커밋 이후에 멤버십 캐시를 무효화한다(#210).
    //
    // 여기서 캐시를 직접 지우지 않는 이유는 트랜잭션 때문이다. 커밋 전에 지우면 그 직후
    // 도착한 메시지 배달이 DB를 다시 읽는데, 그 시점 DB에는 아직 변경이 보이지 않는다.
    // 낡은 값을 되심어 무효화가 없던 일이 된다.
    private void membershipChanged(UUID userId, UUID roomId) {
        eventPublisher.publishEvent(new ChatRoomMembershipChanged(userId, roomId));
    }

    private ChatRooms getGroupRoomOrThrow(UUID roomId) {
        ChatRooms room = chatRoomsRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOMS_001, "채팅방을 찾을 수 없습니다." + roomId));
        if (room.getType() != Chat_type.GROUP) {
            throw new BusinessException(ErrorCode.CHAT_ROOMS_002, "1:1 채팅방에는 사용할 수 없는 기능입니다.");
        }
        return room;
    }

    private ChatRoomMembers requireActiveMember(ChatRooms room, UUID userId) {
        return chatRoomMembersRepository.findByRoom_IdAndUser_Id(room.getId(), userId)
            .filter(ChatRoomMembers::isActive)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_MEMBERS_001, "채팅방의 멤버가 아닙니다." + userId));
    }
}
