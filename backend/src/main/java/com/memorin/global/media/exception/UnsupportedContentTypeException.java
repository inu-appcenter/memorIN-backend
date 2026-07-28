package com.memorin.global.media.exception;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

public class UnsupportedContentTypeException extends BusinessException {

    public UnsupportedContentTypeException(String contentType) {
        super(ErrorCode.MEDIA_001, "Unsupported content type: " + contentType);
    }
}
