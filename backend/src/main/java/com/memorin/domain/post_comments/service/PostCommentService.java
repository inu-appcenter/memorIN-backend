package com.memorin.domain.post_comments.Service;

import com.memorin.domain.post_comments.Entity.PostComments;
import com.memorin.domain.post_comments.Repository.PostCommentRepository;
import com.memorin.domain.posts.Entity.Post;
import com.memorin.domain.posts.Repository.PostRepository;
import com.memorin.domain.users.Entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private final PostCommentRepository postCommentsRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostComments create(UUID postId, UUID authorId, UUID parentId, String body) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001, "존재하지 않는 게시물입니다: " + postId));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "사용자를 찾을 수 없습니다: " + authorId));

        PostComments parent = null;
        if (parentId != null) {
            parent = postCommentsRepository.findActiveById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_003, "부모 댓글이 존재하지 않습니다.: " + parentId));
            // 대댓글의 대댓글까지 허용할지는 정책 문제. 일단 1단계 depth만 허용하려면 아래 체크 추가:
            if (parent.getParent() != null) {
                throw new BusinessException(ErrorCode.COMMENT_004, "대댓글에는 답글을 달 수 없습니다.");
            }
        }

        return postCommentsRepository.save(PostComments.of(post, author, parent, body));
    }

    @Transactional
    public void delete(UUID commentId, UUID requesterId) {
        PostComments comment = postCommentsRepository.findActiveById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_001, "댓글이 존재하지 않습니다.: " + commentId));

        if (!comment.getUser().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.COMMENT_002, "본인만 댓글을 삭제할 수 있습니다.: ");
        }
        comment.softDelete();
    }

    public long countComments(UUID postId) {
        return postCommentsRepository.findActiveByPost(postId).size(); // 목록 화면용. 집계만 필요하면 COUNT 쿼리로 대체 권장
    }
}