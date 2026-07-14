package com.memorin.global.media;

import java.util.UUID;

public class StorageQuotaExceededException extends RuntimeException {

    public StorageQuotaExceededException(UUID userId, long usedBytes, long incomingBytes, long limitBytes) {
        super("Storage quota exceeded for user %s: used=%d, incoming=%d, limit=%d"
                .formatted(userId, usedBytes, incomingBytes, limitBytes));
    }
}
