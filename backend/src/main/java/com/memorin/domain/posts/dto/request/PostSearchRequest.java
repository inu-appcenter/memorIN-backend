package com.memorin.domain.posts.dto.request;

import com.memorin.domain.posts.entity.PostSortType;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.entity.TimeslotType;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

public record PostSearchRequest(
    String keyword,      // 게시물 content(jsonb 원문 텍스트) 대상 부분 문자열 검색. null/blank면 미적용
    List<TagType> tags,          // enum 값들의 AND 매칭, 정확도에선 OR
    TimeslotType timeslot,
    PostSortType sort
) {
}
