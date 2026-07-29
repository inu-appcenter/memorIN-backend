package com.memorin.domain.post_comments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PostCommentCreateRequest(

    @NotBlank(message = "댓글 내용을 입력해주세요.")
    @Size(max = 1000)
    String body,

    UUID parentId // 최상위 댓글이면 null
) { }
