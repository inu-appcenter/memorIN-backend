package com.memorin.global.media.service;

import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.global.media.StorageQuotaProperties;
import com.memorin.global.media.dto.response.QuotaResponse;
import com.memorin.global.media.exception.StorageQuotaExceededException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StorageQuotaService {

    private final PostMediaRepository postMediaRepository;
    private final StorageQuotaProperties properties;

    public StorageQuotaService(PostMediaRepository postMediaRepository, StorageQuotaProperties properties) {
        this.postMediaRepository = postMediaRepository;
        this.properties = properties;
    }

    // 전용 집계 테이블 없이 post_media를 실시간 SUM. (docs/storage-quota-design.md)
    public long getUsedBytes(UUID userId) {
        return postMediaRepository.sumFileSizeBytesByUserId(userId);
    }

    public QuotaResponse getQuotaStatus(UUID userId) {
        long usedBytes = getUsedBytes(userId);
        long limitBytes = properties.defaultLimitBytes();
        long remainingBytes = Math.max(limitBytes - usedBytes, 0);
        double usagePercentage = limitBytes == 0 ? 0.0 : (usedBytes * 100.0) / limitBytes;

        return new QuotaResponse(usedBytes, limitBytes, remainingBytes, usagePercentage);
    }

    public void assertWithinQuota(UUID userId, long incomingBytes) {
        long usedBytes = getUsedBytes(userId);
        long limitBytes = properties.defaultLimitBytes();

        if (usedBytes + incomingBytes > limitBytes) {
            throw new StorageQuotaExceededException(userId, usedBytes, incomingBytes, limitBytes);
        }
    }
}
