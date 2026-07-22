package com.memorin.domain.posts.dto.Request;

import com.memorin.domain.posts.Entity.TimeslotType;
import com.memorin.domain.posts.Entity.VisibilityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


import java.time.LocalDate;
import java.util.List;

public record PostCreateRequest(

        @NotBlank(message = "게시글 내용을 입력해주세요.")
        String content,

        @NotNull(message = "공개 범위를 선택해주세요.")
        VisibilityType visibilityType,

        @NotNull(message = "시간대를 선택해주세요.")
        TimeslotType timeslotType,

        LocalDate recordedDate,

        @Size(max = 10, message = "첨부파일은 최대 10개까지 가능합니다.")
        List<@Valid AttachmentRequest> attachments

) {

    public record AttachmentRequest( // 첨부 파일
            @NotBlank String fileKey,
            @NotBlank String mimeType,
            @NotNull @Positive Long fileSizeBytes,
            Integer width,
            Integer height
    ) {}

}
