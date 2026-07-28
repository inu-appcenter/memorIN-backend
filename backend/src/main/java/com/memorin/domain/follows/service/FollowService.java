package com.memorin.domain.follows.service;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.repository.FollowRepository;
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
@Transactional
public class FollowService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    // 팔로우 요청
    public void request(UUID followerId, UUID followingId) {

        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.FOLLOW_002);
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_001));

        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_001));

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(ErrorCode.FOLLOW_003);
        }

        /* User 엔티티에 공개 범위 기능이 추가되면
        공개 계정 ACCEPTED, 비공개 계정 PENDING으로 구현 */
        Follows follows = new Follows(follower, following);

        followRepository.save(follows);
    }

    // 팔로우 수락
    public void accept(UUID followId, UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Follows follows = followRepository.findById(followId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_001));

        if (!follows.getFollowing().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.FOLLOW_004);
        }

        if (follows.getStatus() != Follow_state.PENDING) {
            throw new BusinessException(ErrorCode.FOLLOW_001);
        }

        follows.accept();
    }

    // 팔로우 거절
    public void reject(UUID followerId, UUID followingId) {

        Follows follows = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_001));

        followRepository.delete(follows);
    }
}
