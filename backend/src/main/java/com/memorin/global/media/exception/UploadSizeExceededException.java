package com.memorin.global.media.exception;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

public class UploadSizeExceededException extends BusinessException {

    public UploadSizeExceededException(long contentLength, long maxUploadSizeBytes) {
        super(ErrorCode.MEDIA_005, "File size exceeds max upload size: contentLength=%d, max=%d"
                .formatted(contentLength, maxUploadSizeBytes));
    }
}
