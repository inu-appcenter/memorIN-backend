package com.memorin.domain.post_likes.service;

import com.memorin.domain.post_likes.entity.PostLikes;
import com.memorin.domain.post_likes.repository.PostLikeRepository;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLikeService {

    private final PostLikeRepository postLikesRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostAccessPolicy postAccessPolicy;

    /** @return true면 좋아요 등록, false면 좋아요 취소 (토글) */
    @Transactional
    public boolean toggleLike(UUID postId, UUID userId) {

        if (postLikesRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikesRepository.deleteByPostIdAndUserId(postId, userId);
            return false;
        } // 이전에 누른 좋아요는 권한에 얽매이지 X 취소 가능하게

        // post 행을 잠가 같은 게시물에 대한 동시 "처음 누르기" 요청을 직렬화한다.
        // 잠금 없이 exists 체크 후 insert하면 두 요청이 동시에 통과해 uq_post_like를 위반하고,
        // Postgres는 제약 위반이 나면 트랜잭션 전체를 abort 상태로 만들어 그 안에서 잡아도
        // 이후 커밋이 실패한다 (StorageQuotaService.reserveUpload와 동일한 TOCTOU 패턴).
        Post post = postRepository.findByIdForUpdate(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001, "존재하지 않는 게시물입니다: " + postId));

        postAccessPolicy.assertReadable(post, userId); // 새로 누르는 것만 검사

        // 잠금을 얻는 사이 다른 트랜잭션이 먼저 좋아요를 넣었을 수 있다 — 멱등하게 성공 처리.
        if (postLikesRepository.existsByPostIdAndUserId(postId, userId)) {
            return true;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "사용자를 찾을 수 없습니다: " + userId));

        postLikesRepository.save(PostLikes.of(post, user));
        return true;
    }

    public long countLikes(UUID postId) {
        return postLikesRepository.countByPostId(postId);
    }

    public boolean isLikedBy(UUID postId, UUID userId) {
        return postLikesRepository.existsByPostIdAndUserId(postId, userId);
    }

    @Transactional(readOnly = true)
    public Post assertReadableForLikes(UUID postId, UUID userId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001, "존재하지 않는 게시물입니다: " + postId));
        postAccessPolicy.assertReadable(post, userId);
        return post;
    }
}
