package com.memorin.global.media;

import java.util.UUID;

public class PostMediaNotFoundException extends RuntimeException {

    public PostMediaNotFoundException(UUID postMediaId) {
        super("Post media not found: " + postMediaId);
    }
}
