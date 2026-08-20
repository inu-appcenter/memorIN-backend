package com.memorin.domain.posts.service;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.posts.entity.Post;
import com.memorin.global.exception.PostExceptions;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 게시물 가시성(visibility) 판단을 한 곳에서 관리한다.
 * 조회(PostService), 댓글(PostCommentsService), 미디어 다운로드가
 * 전부 이 로직을 그대로 재사용해야 "조회는 막혔는데 상호작용은 열려있는" 문제가 재발하지 않는다.
 */
@Component
public class PostAccessPolicy {

    private final FollowRepository followRepository;

    public PostAccessPolicy(FollowRepository followRepository) {

        this.followRepository = followRepository;
    }

    public void assertReadable(Post post, UUID requesterId) {
        switch (post.getVisibility()) {
            case PUBLIC:
                return;

            case PRIVATE:
                if (requesterId == null || !post.isOwnedBy(requesterId)) {
                    throw new PostExceptions.PostAccessDeniedException();
                }

                return;

            case FRIENDS:
                if (requesterId == null) {
                    throw new PostExceptions.PostAccessDeniedException();
                }

                if (post.isOwnedBy(requesterId)) return;

                boolean friend1 = followRepository.existsByFollowerIdAndFollowingIdAndStatus(
                        requesterId,
                        post.getUser().getId(),
                        Follow_state.ACCEPTED
                    );

                boolean friend2 = followRepository.existsByFollowingIdAndFollowerIdAndStatus(
                        requesterId,
                        post.getUser().getId(),
                        Follow_state.ACCEPTED
                    );

                if (!friend1 && !friend2) {
                    throw new PostExceptions.PostAccessDeniedException();
                }
        }
    }
}
