package com.memorin.domain.media_deletion.repository;

import com.memorin.domain.media_deletion.entity.MediaDeletionQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MediaDeletionQueueRepository extends JpaRepository<MediaDeletionQueue, UUID> {

    // 유예 기간이 지난 것만, 오래된 순으로 batch 크기만큼.
    List<MediaDeletionQueue> findByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime cutoff, Pageable pageable);
}
