package com.memorin.domain.posts.dto.Response;


import com.memorin.domain.post_media.Entity.PostMedia;

public record PostMediaResponse(
        String objectKey,
        String[] url,
        String contentType,
        int order,
        Integer width,
        Integer height
) {
    public static PostMediaResponse from(PostMedia media, String presignedUrlResolver) {
        return new PostMediaResponse(
                media.getFileKey(),
                presignedUrlResolver.split(media.getFileKey()),
                media.getMimeType(),
                media.getOrderIndex(),
                media.getWidth(),
                media.getHeight()
        );
    }
}
