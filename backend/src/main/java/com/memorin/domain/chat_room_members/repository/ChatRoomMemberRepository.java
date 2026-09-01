package com.memorin.domain.chat_room_members.repository;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMembers, UUID> {

    boolean existsByRoomIdAndUserId(UUID uuid, UUID senderId);

    // DIRECT 방 하나에 정확히 두 사용자가 모두 활성 멤버로 있는 경우를 찾는다.
    @Query("""
        SELECT m1.room.id FROM ChatRoomMembers m1
        WHERE m1.room.type = com.memorin.domain.chat_rooms.entity.Chat_type.DIRECT
          AND m1.user.id = :userA AND m1.leftAt IS NULL
          AND EXISTS (
              SELECT 1 FROM ChatRoomMembers m2
              WHERE m2.room = m1.room AND m2.user.id = :userB AND m2.leftAt IS NULL
          )
        """)
    Optional<UUID> findActiveDirectRoomId(UUID userA, UUID userB);

    // leftAt 여부와 무관하게 찾는다 — rejoin 처리를 위해 나간 기록도 있어야 함
    Optional<ChatRoomMembers> findByRoom_IdAndUser_Id(UUID roomId, UUID userId);

    List<ChatRoomMembers> findByRoom_IdAndLeftAtIsNull(UUID roomId);

    List<ChatRoomMembers> findByUser_IdAndLeftAtIsNullOrderByJoinedAtDesc(UUID userId);

}
