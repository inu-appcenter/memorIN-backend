package com.memorin.domain.posts.dto.request;

import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** PATCH 요청이므로 모든 필드는 null이면 "변경하지 않음"을 의미한다. */
public record PostUpdateRequest(
        String content,
        VisibilityType visibilityType,
        TimeslotType timeslotType,
        LocalDate recordedDate,
        @Size(max = 10, message = "첨부파일은 최대 10개까지 가능합니다.")
        List<PostCreateRequest.@Valid AttachmentRequest> attachments // null이면 기존 미디어 유지, 배열이면 통째로 교체
) {}
