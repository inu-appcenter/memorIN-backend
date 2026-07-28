package com.memorin.domain.pending_upload.repository;

import com.memorin.domain.pending_upload.entity.PendingUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingUploadRepository extends JpaRepository<PendingUpload, UUID> {

    Optional<PendingUpload> findByObjectKey(String objectKey);

    void deleteByObjectKey(String objectKey);

    // 만료 전 pending 예약만 quota 사용량에 합산한다 (만료된 건 정리 배치가 지우기 전이라도 무시).
    @Query("SELECT COALESCE(SUM(p.reservedBytes), 0) FROM PendingUpload p " +
            "WHERE p.userId = :userId AND p.expiresAt > :now")
    long sumReservedBytesByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    List<PendingUpload> findByExpiresAtBefore(LocalDateTime now);
}
