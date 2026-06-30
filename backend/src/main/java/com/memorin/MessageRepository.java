package com.memorin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 특정 채팅방의 메시지를 id 기준 커서 페이징
    List<Message> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursorId, Pageable pageable);

    // 처음 조회 (커서 없을 때)
    List<Message> findByChatRoomIdOrderByIdDesc(Long roomId, Pageable pageable);
}