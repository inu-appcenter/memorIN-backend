package com.memorin.global.media.service;

import com.memorin.domain.pending_upload.entity.PendingUpload;
import com.memorin.domain.pending_upload.repository.PendingUploadRepository;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.media.StorageQuotaProperties;
import com.memorin.global.media.dto.response.QuotaResponse;
import com.memorin.global.media.exception.StorageQuotaExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class StorageQuotaService {

    private final PostMediaRepository postMediaRepository;
    private final PendingUploadRepository pendingUploadRepository;
    private final UserRepository userRepository;
    private final StorageQuotaProperties properties;
    private final Clock clock;

    @Autowired
    public StorageQuotaService(
            PostMediaRepository postMediaRepository,
            PendingUploadRepository pendingUploadRepository,
            UserRepository userRepository,
            StorageQuotaProperties properties
    ) {
        this(postMediaRepository, pendingUploadRepository, userRepository, properties, Clock.systemUTC());
    }

    StorageQuotaService(
            PostMediaRepository postMediaRepository,
            PendingUploadRepository pendingUploadRepository,
            UserRepository userRepository,
            StorageQuotaProperties properties,
            Clock clock
    ) {
        this.postMediaRepository = postMediaRepository;
        this.pendingUploadRepository = pendingUploadRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    // committed(post_media 실제 합) + pending(만료 전 예약 합). (docs/storage-quota-policy.md)
    public long getUsedBytes(UUID userId) {
        long committed = postMediaRepository.sumFileSizeBytesByUserId(userId);
        long pending = pendingUploadRepository.sumReservedBytesByUserId(userId, LocalDateTime.now(clock));
        return committed + pending;
    }

    public QuotaResponse getQuotaStatus(UUID userId) {
        long usedBytes = getUsedBytes(userId);
        long limitBytes = properties.defaultLimitBytes();
        long remainingBytes = Math.max(limitBytes - usedBytes, 0);
        double usagePercentage = limitBytes == 0 ? 0.0 : (usedBytes * 100.0) / limitBytes;

        return new QuotaResponse(usedBytes, limitBytes, remainingBytes, usagePercentage);
    }

    // TOCTOU 방지: 유저 행을 잠그고 committed+pending 합산 -> 한도 체크 -> pending 삽입까지
    // 하나의 트랜잭션 안에서 원자적으로 수행한다. 같은 유저의 동시 요청은 이 행 락에서 직렬화되므로
    // "잔여 100MB에 90MB 2건 동시 요청 시 둘 다 통과"하는 경쟁 상태가 생기지 않는다.
    @Transactional
    public PendingUpload reserveUpload(UUID userId, String objectKey, long incomingBytes) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "사용자를 찾을 수 없습니다: " + userId));

        long usedBytes = getUsedBytes(userId);
        long limitBytes = properties.defaultLimitBytes();

        if (usedBytes + incomingBytes > limitBytes) {
            throw new StorageQuotaExceededException(userId, usedBytes, incomingBytes, limitBytes);
        }

        LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(properties.pendingTtlSeconds());
        return pendingUploadRepository.save(PendingUpload.of(userId, objectKey, incomingBytes, expiresAt));
    }

    // 커밋 시점 재검증: presigned PUT은 서명이 body 크기를 강제하지 않아 실제 업로드 크기(actualBytes)가
    // 예약 시 선언값(reservedBytes)보다 클 수 있다. getUsedBytes()의 pending 합산엔 이 예약이 여전히
    // reservedBytes로 잡혀 있으므로, 그 선언값을 실제 크기로 바꿔치기해서 다시 합산해야
    // "선언은 작게 하고 실제로는 상한까지 올려 quota를 우회"하는 경로를 막을 수 있다.
    // reserveUpload와 동일하게 유저 행을 잠그고 검증한다 (TOCTOU 방지).
    @Transactional
    public void assertCommitWithinLimit(UUID userId, long reservedBytes, long actualBytes) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "사용자를 찾을 수 없습니다: " + userId));

        long usedBytesExcludingThisReservation = getUsedBytes(userId) - reservedBytes;
        long limitBytes = properties.defaultLimitBytes();

        if (usedBytesExcludingThisReservation + actualBytes > limitBytes) {
            throw new StorageQuotaExceededException(userId, usedBytesExcludingThisReservation, actualBytes, limitBytes);
        }
    }
}
