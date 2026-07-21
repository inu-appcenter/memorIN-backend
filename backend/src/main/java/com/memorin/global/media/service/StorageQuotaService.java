package com.memorin.global.media.service;

import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.global.media.StorageQuotaExceededException;
import com.memorin.global.media.StorageQuotaProperties;
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

    public void assertWithinQuota(UUID userId, long incomingBytes) {
        long usedBytes = getUsedBytes(userId);
        long limitBytes = properties.defaultLimitBytes();

        if (usedBytes + incomingBytes > limitBytes) {
            throw new StorageQuotaExceededException(userId, usedBytes, incomingBytes, limitBytes);
        }
    }
}
