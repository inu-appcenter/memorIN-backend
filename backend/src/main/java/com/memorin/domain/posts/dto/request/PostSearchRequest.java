package com.memorin.domain.posts.dto.request;

import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

public record PostSearchRequest(
    List<TagType> tags,          // enum 값들의 AND 매칭
    TimeslotType timeslot,
    Integer viewCountMin,
    Integer viewCountMax,
    LocalDate recordedDateFrom,
    LocalDate recordedDateTo
) {
}
