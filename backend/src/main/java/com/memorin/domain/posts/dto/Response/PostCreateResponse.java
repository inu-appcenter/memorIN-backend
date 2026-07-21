package com.memorin.domain.posts.dto.Response;

import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.posts.Entity.TimeslotType;
import com.memorin.domain.posts.Entity.VisibilityType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PostCreateResponse(
        String postId,
        String authorId,
        String content,
        VisibilityType visibility,
        TimeslotType timeslot,
        LocalDate recordedDate,
        List<PostMediaResponse> attachments,
        LocalDateTime createdAt
) {
    public static PostCreateResponse of(Post post, List<PostMediaResponse> attachments) {
        return new PostCreateResponse(
                post.getId().toString(),
                post.getUserId().getId().toString(),
                post.getContent(),
                post.getVisibility(),
                post.getTimeslot(),
                post.getRecordedDate().toLocalDate(),
                attachments,
                post.getCreatedAt()
        );
    }
}
