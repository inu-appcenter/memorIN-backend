package com.memorin.domain.notifications.repository;

import com.memorin.domain.notifications.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
        SELECT n FROM Notification n
        LEFT JOIN FETCH n.actor
        WHERE n.user.id = :userId AND (:cursor IS NULL OR n.id < :cursor)
        ORDER BY n.id DESC
    """)
    List<Notification> findNotifications(
        @Param("userId") UUID userId,
        @Param("cursor") UUID cursor,
        Pageable pageable
    );

    Optional<Notification> findByIdAndUserId(
        UUID notificationId,
        UUID userId
    );

    @Modifying
    @Query("""
        UPDATE Notification n SET n.read = true
        WHERE n.user.id = :userId AND n.read = false
    """)
    int readAll(@Param("userId") UUID userId);
}
