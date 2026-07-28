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

    // committed(post_media 실제 합) + pending(만료 전 예약 합). (docs/storage-quota-design.md)
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
}
