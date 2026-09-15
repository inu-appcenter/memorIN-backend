package com.memorin.domain.post_likes.dto.response;

public record PostLikeResponse(

    boolean liked,
    long likeCount

) {
}
