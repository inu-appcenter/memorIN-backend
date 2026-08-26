package com.memorin.domain.post_comments.service;

import com.memorin.domain.emoji.dto.response.EmojiCountDto;
import com.memorin.domain.emoji.dto.response.EmojiSummary;
import com.memorin.domain.emoji.repository.CommentEmojiRepository;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.notifications.service.NotificationService;
import com.memorin.domain.post_comments.dto.response.PostCommentResponse;
import com.memorin.domain.post_comments.entity.PostComments;
import com.memorin.domain.post_comments.repository.PostCommentRepository;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.repository.PostRepository;
import com.memorin.domain.posts.service.PostAccessPolicy;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private final PostCommentRepository postCommentsRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostAccessPolicy postAccessPolicy;
    private final CommentEmojiRepository commentEmojiRepository;
    private final NotificationService notificationService;

    @Transactional
    public PostCommentResponse create(UUID postId, UUID authorId, UUID parentId, String body) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001, "존재하지 않는 게시물입니다: " + postId));

        postAccessPolicy.assertReadable(post, authorId);

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "사용자를 찾을 수 없습니다: " + authorId));

        PostComments parent = null;
        if (parentId != null) {
            parent = postCommentsRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_003, "부모 댓글이 존재하지 않습니다.: " + parentId));

            if (!parent.getPost().getId().equals(postId)) {
               throw new BusinessException(ErrorCode.COMMENT_002, "다른 게시물의 댓글입니다.: " + parentId);
            }

            // 대댓글의 대댓글까지 허용할지는 정책 문제. 일단 1단계 depth만 허용하려면 아래 체크 추가:
            if (parent.getParent() != null) {
                throw new BusinessException(ErrorCode.COMMENT_004, "대댓글에는 답글을 달 수 없습니다.");
            }
        }

        LocalDateTime createdAt = LocalDateTime.now();

        PostComments saved = postCommentsRepository.save(PostComments.of(post, author, parent, body, createdAt));
        UUID recipientId = parent != null ? parent.getUser().getId() : post.getUser().getId();

        if (!recipientId.equals(authorId)) {
            notificationService.save(
                recipientId, authorId, NotificationType.COMMENT,
                parent != null ? "새 답글" : "새 댓글",
                author.getDisplayName() + "님이 댓글을 남겼습니다.",
                saved.getId()
            );
        }

        return PostCommentResponse.from(saved);
    }

    // 목록(스레드) 조회
    //
    // 이모지는 댓글마다 따로 묻지 않고 한 번에 집계한다. 개별 조회로 두면 FE가 댓글 N개마다
    // GET /api/comments/{id}/emojis를 N번 호출하는 HTTP 레벨 N+1이 된다.
    // 댓글 수와 무관하게 SQL은 (게시물 1 + 스레드 1 + 이모지 집계 1)로 고정된다.
    public List<PostCommentResponse> getThread(UUID postId, UUID requesterId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_001, "존재하지 않는 게시물입니다: " + postId));

        postAccessPolicy.assertReadable(post, requesterId); // 가시성 검사

        List<PostComments> thread = postCommentsRepository.findThreadByPostId(postId);
        if (thread.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<EmojiSummary>> emojisByCommentId = aggregateEmojis(thread, requesterId);

        return thread.stream()
            .map(c -> PostCommentResponse.of(c, emojisByCommentId.getOrDefault(c.getId(), List.of())))
            .toList();
    }

    // 스레드에 달린 이모지를 commentId 하나로 묶어 한 번에 가져온다.
    // tombstone(삭제된 댓글)은 이모지를 새로 달 수 없지만 이미 달린 건 남아 있을 수 있어 그대로 집계한다.
    private Map<UUID, List<EmojiSummary>> aggregateEmojis(List<PostComments> thread, UUID requesterId) {
        List<UUID> commentIds = thread.stream().map(PostComments::getId).toList();

        return commentEmojiRepository.countByCommentIds(commentIds, requesterId).stream()
            .collect(Collectors.groupingBy(
                EmojiCountDto::commentId,
                Collectors.mapping(EmojiSummary::from, Collectors.toList())
            ));
    }

    @Transactional
    public PostCommentResponse update(UUID commentId, UUID requesterId, String body) {
        PostComments comment = postCommentsRepository.findActiveById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_001, "댓글이 존재하지 않습니다.: " + commentId));

        if (!comment.getUser().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.COMMENT_002, "본인만 댓글을 수정할 수 있습니다.");
        }

        comment.updateBody(body); // 삭제된 댓글이면 엔티티 내부에서 COMMENT_006 던짐
        return PostCommentResponse.from(comment);
    }


    @Transactional
    public void delete(UUID commentId, UUID requesterId) {
        PostComments comment = postCommentsRepository.findActiveById(commentId) // 해당 매서드 사용 이유: 이미 삭제된 댓글을 또 삭제하려는 요청은 막아야 함.
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_001, "댓글이 존재하지 않습니다.: " + commentId));

        if (!comment.getUser().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.COMMENT_002, "본인만 댓글을 삭제할 수 있습니다.: ");
        }

        comment.softDelete();
    }

    public long countComments(UUID postId) {
        return postCommentsRepository.countActiveByPostId(postId); // 목록 화면용. 집계만 필요하면 COUNT 쿼리로 대체 권장
    }
}
