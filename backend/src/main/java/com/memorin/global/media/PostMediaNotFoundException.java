package com.memorin.global.media;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

import java.util.UUID;

public class PostMediaNotFoundException extends BusinessException {

    public PostMediaNotFoundException(UUID postMediaId) {
        super(ErrorCode.MEDIA_004, "Post media not found: " + postMediaId);
    }
}
