package com.example.demo;

import org.springframework.data.repository.CrudRepository;
import java.util.List;

public interface ChatRoomMemberRepository extends CrudRepository<ChatRoomMember, Long> {
    List<ChatRoomMember> findByChatRoomId(Long roomId);
    boolean existsByChatRoomIdAndUserId(Long roomId, String userId);
}
