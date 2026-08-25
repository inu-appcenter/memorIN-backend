package com.memorin.domain.chat_rooms.repository;

import com.memorin.domain.chat_rooms.entity.ChatRooms;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatRoomsRepository extends CrudRepository<ChatRooms, UUID> {
    Optional<ChatRooms> findById(UUID uuid);
}
