package com.memorin.domain.posts.dto.Response;


import com.memorin.domain.post_media.Entity.PostMedia;

public record PostMediaResponse(
        String objectKey,
        // presigned 다운로드 URL. 발급에 실패하면 null.
        String url,
        String contentType,
        int order,
        Integer width,
        Integer height
) {
    public static PostMediaResponse from(PostMedia media, String presignedDownloadUrl) {
        return new PostMediaResponse(
                media.getFileKey(),
                presignedDownloadUrl,
                media.getMimeType(),
                media.getOrderIndex(),
                media.getWidth(),
                media.getHeight()
        );
    }
}
