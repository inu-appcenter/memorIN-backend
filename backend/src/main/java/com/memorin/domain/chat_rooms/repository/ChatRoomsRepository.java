package com.memorin.domain.chat_rooms.repository;

import com.memorin.domain.chat_rooms.entity.ChatRooms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// CrudRepository가 아니라 JpaRepository를 쓴다.
// CrudRepository<T, ID>의 findById는 Optional<T>를 주지만, 이 인터페이스가 예전에
// CrudRepository<ChatRooms, Long>으로 선언돼 있던 흔적 때문에 호출부(MessageService)에
// (ChatRooms) 캐스팅이 남아 있었다. 타입을 바로잡으면 캐스팅도 findById 재선언도 필요 없다.
public interface ChatRoomsRepository extends JpaRepository<ChatRooms, UUID> {
}
