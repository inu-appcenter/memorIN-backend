package com.memorin.domain.chat_room_members.repository;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ChatRoomMemberRepository extends CrudRepository<ChatRoomMembers, Long> {
    boolean existsByRoomIdAndUserId(UUID uuid, UUID senderId);
}
