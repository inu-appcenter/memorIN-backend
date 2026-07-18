package com.memorin.domain.posts.Service;

import com.memorin.domain.post_media.Entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.posts.Repository.PostRepository;
import com.memorin.domain.posts.dto.Request.PostCreateRequest;
import com.memorin.domain.posts.dto.Request.PostUpdateRequest;
import com.memorin.domain.posts.dto.Response.*;
import com.memorin.domain.users.Entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.exception.PostExceptions;
import com.memorin.global.media.service.PresignedDownloadService;
import com.memorin.global.media.service.PresignedUploadService;
import com.memorin.global.media.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final UserRepository userRepository;
    private final PresignedDownloadService presignedDownloadService;
    private final PresignedUploadService presignedUploadService;
    private final StorageQuotaService storageQuotaService;

    // 글 등록
    @Transactional
    public PostCreateResponse create(UUID authorId, PostCreateRequest request) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + authorId));

        Date recordedDate = request.recordedDate() != null
                ? Date.valueOf(request.recordedDate())
                : Date.valueOf(LocalDate.now());

        Post post = Post.create(author, request.content(), request.visibilityType(),
                request.timeslotType(), recordedDate);
        postRepository.save(post);

        List<PostMedia> savedMedia = saveMedia(post, request.attachments());

        List<PostMediaResponse> attachmentResponses = toMediaResponses(savedMedia);
        return PostCreateResponse.of(post, attachmentResponses);
    }

    // ---- 단건 조회 ----
    @Transactional
    public PostResponse getOne(UUID postId, UUID requesterId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new PostExceptions.PostNotFoundException(postId.toString()));

        assertReadable(post, requesterId);

        // 작성자 본인이 아닐 때만 조회수 증가 (읽기 API인데 굳이 트랜잭션 열어서 처리)
        if (requesterId == null || !post.isOwnedBy(requesterId)) {
            post.increaseViewCount();
        }

        List<PostMedia> media = postMediaRepository.findByPost_IdOrderByOrderIndexAsc(postId);
        return PostResponse.of(post, toMediaResponses(media));
    }

    // ---- 목록(피드) 조회 ----
    // targetUserId가 없으면 "내 기록" 목록으로 간주하고 requesterId를 사용한다.
    public PostListResponse list(UUID targetUserId, UUID requesterId, String cursor, Integer size) {
        UUID userId = targetUserId != null ? targetUserId : requesterId;
        if (userId == null) {
            throw new IllegalArgumentException("조회할 사용자를 특정할 수 없습니다.");
        }
        boolean includeAllVisibility = userId.equals(requesterId);

        int limit = normalizeSize(size);

        Date cursorRecordedDate = null;
        String cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            PostCursor.Cursor decoded = PostCursor.decode(cursor);
            cursorRecordedDate = decoded.recordedDate();
            cursorId = decoded.postId();
        }

        // limit + 1개를 조회해서 다음 페이지 존재 여부만 판단 (별도 count 쿼리 없이).
        List<Post> rows = postRepository.findUserFeed(
                userId.toString(), includeAllVisibility, cursorRecordedDate, cursorId, limit + 1
        );

        boolean hasNext = rows.size() > limit;
        List<Post> pageContent = hasNext ? rows.subList(0, limit) : rows;

        // N+1 방지: 페이지에 포함된 게시물들의 미디어를 한 번에 가져와서 postId로 그룹핑.
        List<UUID> postIds = pageContent.stream().map(Post::getId).toList();
        Map<UUID, List<PostMedia>> mediaByPostId = postMediaRepository
                .findByPost_IdInOrderByOrderIndexAsc(postIds).stream()
                .collect(Collectors.groupingBy(m -> m.getPost().getId()));

        List<PostSummaryResponse> items = pageContent.stream()
                .map(p -> PostSummaryResponse.of(p, toMediaResponses(mediaByPostId.getOrDefault(p.getId(), List.of()))))
                .toList();

        String nextCursor = null;
        if (hasNext && !pageContent.isEmpty()) {
            Post last = pageContent.get(pageContent.size() - 1);
            nextCursor = PostCursor.encode(last.getRecordedDate(), last.getId().toString());
        }

        return new PostListResponse(items, nextCursor, hasNext);
    }

    // ---- 수정 ----
    @Transactional
    public PostResponse update(UUID postId, UUID requesterId, PostUpdateRequest request) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new PostExceptions.PostNotFoundException(postId.toString()));

        if (!post.isOwnedBy(requesterId)) {
            throw new PostExceptions.PostAccessDeniedException();
        }

        Date recordedDate = request.recordedDate() != null ? Date.valueOf(request.recordedDate()) : null;
        post.update(request.content(), request.visibilityType(), request.timeslotType(), recordedDate);

        List<PostMedia> media;
        if (request.attachments() != null) {
            // attachments가 명시적으로 온 경우: 기존 미디어를 통째로 교체.
            postMediaRepository.deleteAllByPostId(postId);
            media = saveMedia(post, request.attachments());
        } else {
            media = postMediaRepository.findByPost_IdOrderByOrderIndexAsc(postId);
        }

        return PostResponse.of(post, toMediaResponses(media));
    }

    // ---- 삭제 (소프트) ----
    @Transactional
    public void delete(UUID postId, UUID requesterId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new PostExceptions.PostNotFoundException(postId.toString()));

        if (!post.isOwnedBy(requesterId)) {
            throw new PostExceptions.PostAccessDeniedException();
        }
        post.softDelete();
        // post_media row는 그대로 둔다. 실제 MinIO 객체 정리는 별도 배치/정책으로 처리하는 것을 권장.
    }

    // ---- private helpers ----

    private List<PostMedia> saveMedia(Post post, List<PostCreateRequest.AttachmentRequest> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<PostMedia> entities = new ArrayList<>();
        int order = 0;
        for (PostCreateRequest.AttachmentRequest a : attachments) {
            entities.add(PostMedia.of(
                    post, a.fileKey(), a.mimeType(), a.fileSizeBytes(), (short) order++, a.width(), a.height()
            ));
        }
        return postMediaRepository.saveAll(entities);
    }
// 여기 수정해야 함. 여기 수정해야 함.여기 수정해야 함.여기 수정해야 함.여기 수정해야 함.
    private List<PostMediaResponse> toMediaResponses(List<PostMedia> media) {
        return media.stream()
                .map(m -> PostMediaResponse.from(m, Objects.requireNonNull(resolveDownloadUrl(m.getId()))))
                .toList();
}

    private String resolveDownloadUrl(UUID postMediaId) {
        try {
            return presignedDownloadService.createDownloadUrl(postMediaId).downloadUrl();
        } catch (Exception e) {
            // 미디어 하나의 URL 발급 실패로 게시물 조회 전체가 실패하지 않도록 null 처리.
            return null;
        }
    }

    private void assertReadable(Post post, UUID requesterId) {
        switch (post.getVisibility()) {
            case PUBLIC -> { /* 누구나 조회 가능 */ }
            case PRIVATE -> {
                if (requesterId == null || !post.isOwnedBy(requesterId)) {
                    throw new PostExceptions.PostAccessDeniedException();
                }
            }
            case FRIENDS -> {
                // TODO: follows 테이블 연동 후 팔로우 관계 확인 로직으로 교체. 현재는 본인만 허용.
                if (requesterId == null || !post.isOwnedBy(requesterId)) {
                    throw new PostExceptions.PostAccessDeniedException();
                }
            }
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }


}
