package com.memorin.global.media.exception;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

import java.util.UUID;

public class MediaAccessDeniedException extends BusinessException {

    public MediaAccessDeniedException(UUID postMediaId) {
        super(ErrorCode.MEDIA_006, "Access denied to post media: " + postMediaId);
    }
}
