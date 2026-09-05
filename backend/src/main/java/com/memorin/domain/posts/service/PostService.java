package com.memorin.domain.posts.service;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.domain.posts.dto.request.PostSearchRequest;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.PostSortType;
import com.memorin.domain.posts.entity.TagType;
import com.memorin.domain.posts.repository.PostRepository;
import com.memorin.domain.posts.dto.request.PostCreateRequest;
import com.memorin.domain.posts.dto.request.PostUpdateRequest;
import com.memorin.domain.posts.dto.response.*;
import com.memorin.domain.posts.repository.PostSearchRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.global.exception.PostExceptions;
import com.memorin.global.media.service.MediaUploadCommitService;
import com.memorin.global.media.service.PresignedDownloadService;
import com.memorin.global.media.service.PresignedUploadService;
import com.memorin.global.media.service.StorageQuotaService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final PostSearchRepository postSearchRepository;
    private final PostMediaRepository postMediaRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PresignedDownloadService presignedDownloadService;
    private final PresignedUploadService presignedUploadService;
    private final StorageQuotaService storageQuotaService;
    private final MediaUploadCommitService mediaUploadCommitService;
    private final PostAccessPolicy postAccessPolicy;

    // 글 등록
    @Transactional
    public PostCreateResponse create(UUID authorId, PostCreateRequest request) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "사용자를 찾을 수 없습니다: " + authorId));

        Date recordedDate = request.recordedDate() != null
                ? Date.valueOf(request.recordedDate())
                : Date.valueOf(LocalDate.now());

        @Size(max = 3, message = "태그는 최대 3개까지 선택할 수 있습니다.") List<TagType> tags = request.tags();

        Post post = Post.create(author, request.content(), request.visibilityType(),
                request.timeslotType(), recordedDate, tags);
        postRepository.saveAndFlush(post);

        List<PostMedia> savedMedia = saveMedia(post, request.attachments(), authorId);

        List<PostMediaResponse> attachmentResponses = toMediaResponses(savedMedia);
        return PostCreateResponse.of(post, attachmentResponses);
    }

    // ---- 단건 조회 ----
    @Transactional
    public PostResponse getOne(UUID postId, UUID requesterId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new PostExceptions.PostNotFoundException(postId.toString()));

        postAccessPolicy.assertReadable(post, requesterId);

        // 작성자 본인이 아닐 때만 조회수 증가 (읽기 API인데 굳이 트랜잭션 열어서 처리)
        if (requesterId == null || !post.isOwnedBy(requesterId)) {
            post.increaseViewCount();
        }

        List<PostMedia> media = postMediaRepository.findByPostIdOrderByOrderIndexAsc(postId);
        return PostResponse.of(post, toMediaResponses(media));
    }

    // ---- 목록(피드) 조회 ----
    // 날짜 범위 없이 부르는 기존 호출부용. 캘린더가 아닌 일반 피드는 이쪽을 그대로 쓴다.
    public PostListResponse list(UUID targetUserId, UUID requesterId, String cursor, Integer size) {
        return list(targetUserId, requesterId, cursor, size, null, null);
    }

    // targetUserId가 없으면 "내 기록" 목록으로 간주하고 requesterId를 사용한다.
    // from/to는 캘린더 뷰용 recorded_date 범위 필터다. 둘 다 null이면 전체 기간.
    public PostListResponse list(UUID targetUserId, UUID requesterId, String cursor, Integer size,
                                 LocalDate from, LocalDate to) {
        UUID userId = targetUserId != null ? targetUserId : requesterId;
        if (userId == null) {
            throw new BusinessException(ErrorCode.COMMON_002, "조회할 사용자를 특정할 수 없습니다.");
        }
        boolean includeAllVisibility = userId.equals(requesterId);

        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.COMMON_002, "from이 to보다 늦을 수 없습니다: from=" + from + ", to=" + to);
        }
        Date fromDate = from != null ? Date.valueOf(from) : null;
        Date toDate = to != null ? Date.valueOf(to) : null;

        int limit = normalizeSize(size);

        Date cursorRecordedDate = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            PostCursor.Cursor decoded = PostCursor.decode(cursor);
            cursorRecordedDate = decoded.recordedDate();
            cursorId = UUID.fromString(decoded.postId());
        }

        // limit + 1개를 조회해서 다음 페이지 존재 여부만 판단 (별도 count 쿼리 없이).
        List<Post> rows = postRepository.findUserFeed(
                userId,
                requesterId,
                includeAllVisibility,
                fromDate,
                toDate,
                cursorRecordedDate,
                cursorId,
                limit + 1
            );

        boolean hasNext = rows.size() > limit;
        List<Post> pageContent = hasNext ? rows.subList(0, limit) : rows;

        // N+1 방지: 페이지에 포함된 게시물들의 미디어를 한 번에 가져와서 postId로 그룹핑.
        List<UUID> postIds = pageContent.stream().map(Post::getId).toList();
        Map<UUID, List<PostMedia>> mediaByPostId = postMediaRepository
                .findByPostIdInOrderByOrderIndexAsc(postIds).stream()
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

    public PostListResponse search(UUID viewerId, PostSearchRequest condition, String cursor, Integer size) {
        boolean hasKeyword = condition.keyword() != null && !condition.keyword().isBlank();
        boolean hasTags = condition.tags() != null && !condition.tags().isEmpty();

        if (condition.sort() == PostSortType.ACCURACY_DESC && !hasKeyword && !hasTags) {
            throw new BusinessException(ErrorCode.POST_003, "정확도순 정렬은 검색어 또는 태그 중 하나는 필요합니다.");
        }

        int limit = normalizeSize(size);

        PostCursor.Cursor decoded = null;
        if (cursor != null && !cursor.isBlank()) {
            decoded = PostCursor.decode(cursor);
        }

        // postSearchRepository.search()가 "공개 글이거나 본인 글"로 이미 필터링해서 내려주므로,
        // 여기서 만드는 미디어 URL도 전부 열람 권한이 확인된 게시물 소속이다.
        List<Post> rows = postSearchRepository.search(viewerId, condition, decoded, limit + 1);

        boolean hasNext = rows.size() > limit;
        List<Post> pageContent = hasNext ? rows.subList(0, limit) : rows;

        List<UUID> postIds = pageContent.stream().map(Post::getId).toList();
        Map<UUID, List<PostMedia>> mediaByPostId = postMediaRepository
            .findByPostIdInOrderByOrderIndexAsc(postIds).stream()
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
            media = saveMedia(post, request.attachments(), requesterId);
        } else {
            media = postMediaRepository.findByPostIdOrderByOrderIndexAsc(postId);
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

    // 첨부마다 pending 예약을 커밋(statObject로 실제 크기 재검증)한 뒤 저장한다.
    // a.fileSizeBytes()(클라이언트 선언값)는 신뢰하지 않고 검증된 실제 크기를 쓴다.
    private List<PostMedia> saveMedia(Post post, List<PostCreateRequest.AttachmentRequest> attachments, UUID requesterId) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<PostMedia> entities = new ArrayList<>();
        int order = 0;
        for (PostCreateRequest.AttachmentRequest a : attachments) {
            long verifiedBytes = mediaUploadCommitService.commitUpload(requesterId, a.fileKey());
            entities.add(PostMedia.of(
                    post, a.fileKey(), a.mimeType(), verifiedBytes, (short) order++, a.width(), a.height()
            ));
        }
        return postMediaRepository.saveAll(entities);
    }
    // URL 발급에 실패한 미디어는 url이 null로 내려간다.
    // requireNonNull로 감싸면 미디어 한 건의 실패가 게시물 조회 전체를 500으로 만든다.
    private List<PostMediaResponse> toMediaResponses(List<PostMedia> media) {
        return media.stream()
                .map(m -> PostMediaResponse.from(m, resolveDownloadUrl(m)))
                .toList();
    }

    private String resolveDownloadUrl(PostMedia media) {
        try {
            return presignedDownloadService.createDownloadUrl(media).downloadUrl();
        } catch (Exception e) {
            // 미디어 하나의 URL 발급 실패로 게시물 조회 전체가 실패하지 않도록 null 처리.
            return null;
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    // isFriend()는 여기 있었으나 호출부가 하나도 없는 죽은 메서드였다(#141).
    // 친구 판정은 PostAccessPolicy.assertReadable 하나로만 한다 — 단건·댓글·미디어가
    // 전부 그쪽을 쓰고, 목록(findUserFeed)도 같은 양방향 조건을 SQL로 갖고 있다.
    // 판정 로직을 다시 여기에 만들면 "목록엔 보이는데 눌러 들어가면 403"이 재발한다.

    public PostListResponse friendFeed(UUID userId, String cursor, Integer size) {

        List<UUID> followingIds = followRepository.findFollowingIds(userId, Follow_state.ACCEPTED);

        if (followingIds.isEmpty()) {
            return new PostListResponse(List.of(), null, false);
        }

        int limit = normalizeSize(size);

        Date cursorRecordedDate = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            PostCursor.Cursor decoded = PostCursor.decode(cursor);
            cursorRecordedDate = decoded.recordedDate();
            cursorId = UUID.fromString(decoded.postId());
        }

        List<Post> rows = postRepository.findFriendFeed(
            followingIds,
            cursorRecordedDate,
            cursorId,
            limit + 1
        );

        // limit + 1개를 조회해서 다음 페이지 존재 여부만 판단 (별도 count 쿼리 없이).
        boolean hasNext = rows.size() > limit;
        List<Post> pageContent = hasNext ? rows.subList(0, limit) : rows;

        List<UUID> postIds = new ArrayList<>();

        for (Post post : pageContent) {
            postIds.add(post.getId());
        }

        List<PostMedia> mediaList = postMediaRepository.findByPostIdInOrderByOrderIndexAsc(postIds);

        Map<UUID, List<PostMedia>> mediaByPostId = new HashMap<>();

        for (PostMedia media : mediaList) {
            UUID postId = media.getPost().getId();
            // computeIfAbsent(key, k -> new ArrayList<>())의 뜻
            // => map에서 키가 없으면 빈 ArrayList를 만들고,
            // 있으면 그 값을 그대로 꺼내옴. 그리고 거기에 media를 add
            mediaByPostId.computeIfAbsent(postId, k -> new ArrayList<>()).add(media);
        }

        List<PostSummaryResponse> items = new ArrayList<>();

        for (Post post : pageContent) {
            List<PostMedia> media = mediaByPostId.get(post.getId());

            if (media == null) {
                media = new ArrayList<>();
            }

            items.add(PostSummaryResponse.of(post, toMediaResponses(media)));
        }

        String nextCursor = null;

        if (hasNext) {
            Post last = pageContent.get(pageContent.size() - 1);

            nextCursor = PostCursor.encode(
                last.getRecordedDate(),
                last.getId().toString()
            );
        }

        return new PostListResponse(items, nextCursor, hasNext);
    }
}
