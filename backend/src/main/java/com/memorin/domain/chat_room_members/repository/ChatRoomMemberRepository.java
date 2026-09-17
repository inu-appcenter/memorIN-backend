package com.memorin.domain.chat_room_members.repository;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.chat_room_members.repository.projection.RoomActivityProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
<<<<<<< Updated upstream

=======
>>>>>>> Stashed changes

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMembers, UUID> {

    // 멤버 여부를 묻는 메서드는 이것 하나뿐이다.
    // leftAt을 보지 않는 변형(existsByRoomIdAndUserId)이 함께 있었는데, chat_room_members는
    // uq_room_member 제약 때문에 나갈 때 행을 지우지 않고 leftAt만 채운다. 그래서 그 변형을 쓰면
    // 나간 사람도 강퇴당한 사람도 검사를 통과한다. 실제로 MessageService가 그걸 쓰고 있었다.
    // 선택지를 남겨두면 다음 사람이 또 잘못 고르므로 아예 없앴다.
    boolean existsByRoom_IdAndUser_IdAndLeftAtIsNull(UUID roomId, UUID userId);

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

    // 채팅방 목록 화면이 방 이름·타입을 바로 읽으므로 room을 함께 가져온다.
    // 이게 없으면 m.getRoom()이 LAZY 프록시라 방 개수만큼 SELECT가 추가로 나간다(N+1).
    // 첫 화면이라 방이 20개면 쿼리도 21개가 된다. ChatRoomListQueryCountTest가 이 숫자를 고정한다.
    @EntityGraph(attributePaths = "room")
    List<ChatRoomMembers> findByUser_IdAndLeftAtIsNullOrderByJoinedAtDesc(UUID userId);

<<<<<<< Updated upstream
    // 방 목록 화면의 안읽은 수·마지막 메시지를 방 개수와 무관하게 쿼리 1번으로 집계한다(#258).
    // 안읽음 기준 시각은 GREATEST(last_read_at, joined_at) — rejoin()이 last_read_at은 그대로
    // 둔 채 joined_at만 갱신하므로, 재입장 전에 읽었던 시각을 그대로 쓰면 자리를 비운 동안
    // 쌓인 메시지까지 읽음으로 잘못 처리된다.
    // 두 LATERAL 서브쿼리 모두 idx_messages_room_id(room_id, sent_at DESC)를 타므로
    // 방이 N개여도 실행계획상 인덱스 룩업 N번일 뿐 SQL 문장 자체는 이 한 줄이다.
    @Query(value = """
        SELECT
            crm.room_id AS roomId,
            COALESCE(uc.unread_count, 0) AS unreadCount,
            lm.id AS lastMessageId,
            lm.type AS lastMessageType,
            lm.content ->> 'text' AS lastMessageText,
            lm.sent_at AS lastMessageSentAt
        FROM chat_room_members crm
        LEFT JOIN LATERAL (
            SELECT COUNT(*) AS unread_count
            FROM messages m
            WHERE m.room_id = crm.room_id
              AND m.deleted_at IS NULL
              AND m.sent_at > GREATEST(crm.last_read_at, crm.joined_at)
              AND m.sender_id <> crm.user_id
        ) uc ON TRUE
        LEFT JOIN LATERAL (
            SELECT m.id, m.type, m.content, m.sent_at
            FROM messages m
            WHERE m.room_id = crm.room_id AND m.deleted_at IS NULL
            ORDER BY m.sent_at DESC
            LIMIT 1
        ) lm ON TRUE
        WHERE crm.user_id = :userId AND crm.left_at IS NULL
        """, nativeQuery = true)
    List<RoomActivityProjection> findRoomActivityByUserId(@Param("userId") UUID userId);
=======
    @Query("""
    SELECT m.user.id
    FROM ChatRoomMembers m
    WHERE m.room.id = :roomId
      AND m.leftAt IS NULL
      AND m.user.id <> :senderId
    """)
    List<UUID> findActiveMemberIdsExcludingSender(
        @Param("roomId") UUID roomId,
        @Param("senderId") UUID senderId
    );
>>>>>>> Stashed changes

}
