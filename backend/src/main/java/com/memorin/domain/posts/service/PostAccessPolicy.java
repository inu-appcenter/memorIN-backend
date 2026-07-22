package com.memorin.domain.posts.service;

import com.memorin.domain.posts.entity.Post;
import com.memorin.global.exception.PostExceptions;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 게시물 가시성(visibility) 판단을 한 곳에서 관리한다.
 * 조회(PostService), 댓글(PostCommentsService), 좋아요(PostLikesService)가
 * 전부 이 로직을 그대로 재사용해야 "조회는 막혔는데 상호작용은 열려있는" 문제가 재발하지 않는다.
 */
@Component
public class PostAccessPolicy {

    public static void assertReadable(Post post, UUID requesterId) {
        switch (post.getVisibility()) {
            case PUBLIC -> { /* 누구나 접근 가능 */ }
            case PRIVATE -> {
                if (requesterId == null || !post.isOwnedBy(requesterId)) {
                    throw new PostExceptions.PostAccessDeniedException();
                }
            }
            case FRIENDS -> {
                // TODO: follows 연동 후 팔로우 관계 확인 로직으로 교체. 현재는 본인만 허용.
                if (requesterId == null || !post.isOwnedBy(requesterId)) {
                    throw new PostExceptions.PostAccessDeniedException();
                }
            }
        }
    }
}