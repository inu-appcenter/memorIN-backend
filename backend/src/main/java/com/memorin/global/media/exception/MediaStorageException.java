package com.memorin.global.media.exception;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

public class MediaStorageException extends BusinessException {

    public MediaStorageException(String message, Throwable cause) {
        super(ErrorCode.MEDIA_003, message, cause);
    }
}
