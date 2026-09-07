package com.memorin.domain.messages.repository;

import com.memorin.domain.messages.entity.Messages;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Messages, UUID> {

    @Query("""
        SELECT m FROM Messages m
        JOIN FETCH m.sender
        WHERE m.room.id = :roomId
          AND m.deletedAt IS NULL
          AND (:cursor IS NULL OR m.id < :cursor)
        ORDER BY m.id DESC
    """)
    List<Messages> findMessages(
        @Param("roomId") UUID roomId,
        @Param("cursor") UUID cursor,
        Pageable pageable
    );
}
