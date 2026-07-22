package com.memorin.domain.posts.dto.response;

import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        String postId,
        String authorId,
        String content,
        VisibilityType visibility,
        TimeslotType timeslot,
        LocalDate recordedDate,
        int viewCount,
        List<PostMediaResponse> attachments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostResponse of(Post post, List<PostMediaResponse> attachments) {
        return new PostResponse(
                post.getId().toString(),
                post.getUser().getId().toString(),
                post.getContent(),
                post.getVisibility(),
                post.getTimeslot(),
                post.getRecordedDate().toLocalDate(),
                post.getViewCount(),
                attachments,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
