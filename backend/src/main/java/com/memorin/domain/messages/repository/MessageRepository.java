package com.memorin.domain.messages.repository;

import com.memorin.domain.messages.entity.Messages;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Messages, UUID> {
}
