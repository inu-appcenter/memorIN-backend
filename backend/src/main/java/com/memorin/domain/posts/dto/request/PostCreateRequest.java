package com.memorin.domain.posts.dto.request;

import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.global.validation.ValidJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


import java.time.LocalDate;
import java.util.List;

public record PostCreateRequest(

        @NotBlank(message = "게시글 내용을 입력해주세요.")
        @Size(max = CONTENT_MAX_LENGTH, message = "게시글 내용이 너무 깁니다.")
        @ValidJson(message = "게시글 내용이 올바른 JSON 형식이 아닙니다.")
        String content,

        @NotNull(message = "공개 범위를 선택해주세요.")
        VisibilityType visibilityType,

        @NotNull(message = "시간대를 선택해주세요.")
        TimeslotType timeslotType,

        LocalDate recordedDate,

        @Size(max = 10, message = "첨부파일은 최대 10개까지 가능합니다.")
        List<@Valid AttachmentRequest> attachments,

        @Size(max = 3, message = "태그는 최대 3개까지 선택할 수 있습니다.")
        List<TagType> tags

) {

    // 게시물 본문(content, jsonb) 최대 길이(문자 수).
    // 스토리지 할당량은 MinIO 미디어만 세므로, DB로 들어가는 content는 여기서 별도로 상한을 둔다(#244).
    // 텍스트 기록 서비스라 넉넉히 잡되(한 편의 매우 긴 기록도 수용) 무제한은 아니게 한다. 값은 리뷰에서 조정 가능.
    public static final int CONTENT_MAX_LENGTH = 100_000;

    public record AttachmentRequest( // 첨부 파일
            @NotBlank String fileKey,
            @NotBlank String mimeType,
            @NotNull @Positive Long fileSizeBytes,
            Integer width,
            Integer height
    ) {}

}
