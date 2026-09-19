package com.memorin.domain.post_likes.event;

import java.util.UUID;

public record PostLiked(
    UUID postId,
    UUID recipientId,
    UUID actorId,
    String actorDisplayName
) {
}
