package com.memorin.domain.posts.service;

import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.domain.posts.dto.response.PostMediaResponse;
import com.memorin.global.media.service.PresignedDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 목록/피드 응답에 첨부 미디어를 붙인다.
//
// 게시물마다 미디어를 따로 조회하면 그게 곧 N+1이다. 한 번의 IN 조회로 전부 가져와
// postId로 묶어 두고 꺼내 쓴다 (docs/n+1-audit.md §3).
@Component
@RequiredArgsConstructor
public class PostMediaAttacher {

    private final PostMediaRepository postMediaRepository;
    private final PresignedDownloadService presignedDownloadService;

    public Map<UUID, List<PostMediaResponse>> byPostId(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<PostMediaResponse>> byPostId = new HashMap<>();

        for (PostMedia media : postMediaRepository.findByPostIdInOrderByOrderIndexAsc(postIds)) {
            // getPost()는 지연 로딩 프록시지만 getId()는 추가 쿼리 없이 읽힌다.
            byPostId.computeIfAbsent(media.getPost().getId(), k -> new ArrayList<>())
                    .add(PostMediaResponse.from(media, resolveDownloadUrl(media)));
        }

        return byPostId;
    }

    private String resolveDownloadUrl(PostMedia media) {
        try {
            return presignedDownloadService.createDownloadUrl(media).downloadUrl();
        } catch (Exception e) {
            // 미디어 하나의 URL 발급 실패로 피드 전체가 실패하지 않도록 null 처리.
            return null;
        }
    }
}
