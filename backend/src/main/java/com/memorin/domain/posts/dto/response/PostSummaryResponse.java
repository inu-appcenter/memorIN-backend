package com.memorin.domain.posts.dto.response;

import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;

import java.time.LocalDate;
import java.util.List;

public record PostSummaryResponse(
        String postId,
        String authorId,
        String content,
        VisibilityType visibility,
        TimeslotType timeslot,
        LocalDate recordedDate,
        int viewCount,
        List<PostMediaResponse> attachments
) {
    public static PostSummaryResponse of(Post post, List<PostMediaResponse> attachments) {
        return new PostSummaryResponse(
                post.getId().toString(),
                post.getUser().getId().toString(),
                post.getContent(),
                post.getVisibility(),
                post.getTimeslot(),
                post.getRecordedDate().toLocalDate(),
                post.getViewCount(),
                attachments
        );
    }
}
