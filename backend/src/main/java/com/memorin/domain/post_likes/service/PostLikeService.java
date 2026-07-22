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

    /** @return true면 좋아요 등록, false면 좋아요 취소 (토글) */
    @Transactional
    public boolean toggleLike(UUID postId, UUID userId) {

        if (postLikesRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikesRepository.deleteByPostIdAndUserId(postId, userId);
            return false;
        } // 이전에 누른 좋아요는 권한에 얽매이지 X 취소 가능하게

        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001, "존재하지 않는 게시물입니다: " + postId));
        PostAccessPolicy.assertReadable(post, userId); // 새로 누르는 것만 검사

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001, "사용자를 찾을 수 없습니다: " + userId));

        try {
            postLikesRepository.saveAndFlush(PostLikes.of(post, user));
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 더블탭 등으로 동시에 두 요청이 들어와 uq_post_like 제약에 걸린 경우.
            // 최종 상태는 "좋아요가 등록됨"과 동일하므로 예외를 삼키고 멱등하게 성공 처리한다.
            return true;
        }
    }

    public long countLikes(UUID postId) {
        return postLikesRepository.countByPostId(postId);
    }
}