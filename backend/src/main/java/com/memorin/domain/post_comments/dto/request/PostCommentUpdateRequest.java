package com.memorin.domain.post_comments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCommentUpdateRequest(
    @NotBlank(message = "댓글 내용을 입력해주세요.")
    @Size(max = 1000)
    String body
) {
}
