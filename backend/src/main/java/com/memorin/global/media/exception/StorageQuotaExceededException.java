package com.memorin.global.media.exception;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

import java.util.UUID;

public class StorageQuotaExceededException extends BusinessException {

    public StorageQuotaExceededException(UUID userId, long usedBytes, long incomingBytes, long limitBytes) {
        super(ErrorCode.MEDIA_002, "Storage quota exceeded for user %s: used=%d, incoming=%d, limit=%d"
                .formatted(userId, usedBytes, incomingBytes, limitBytes));
    }
}
