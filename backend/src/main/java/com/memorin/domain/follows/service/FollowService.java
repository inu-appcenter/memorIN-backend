package com.memorin.domain.follows.service;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.notifications.entity.NotificationType;
import com.memorin.domain.notifications.service.NotificationService;
import com.memorin.domain.users.dto.UserFollowRequestResponse;
import com.memorin.domain.users.dto.UserFollowRequestPageResponse;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FollowService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final NotificationService notificationService;

    // 팔로우 요청
    public void request(UUID followerId, UUID followingId) {

        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.FOLLOW_002);
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(ErrorCode.FOLLOW_003);
        }

        /* User 엔티티에 공개 범위 기능이 추가되면
        공개 계정 ACCEPTED, 비공개 계정 PENDING으로 구현 */
        Follows follows = new Follows(follower, following);
        Follows saved = followRepository.save(follows);

        notificationService.save(
            followingId, followerId, NotificationType.FOLLOW_REQUEST,
            "새 팔로우 요청", follower.getDisplayName() + "님이 팔로우를 요청했습니다.", saved.getId()
        );

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
            throw new BusinessException(ErrorCode.FOLLOW_005);
        }

        follows.accept();

        notificationService.save(
            follows.getFollower().getId(), userId, NotificationType.FOLLOW_ACCEPTED,
            "팔로우 요청 수락", user.getDisplayName() + "님이 팔로우 요청을 수락했습니다.", follows.getId()
        );
    }

    // 받은 팔로우 요청 거절
    public void reject(UUID followId, UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Follows follows = followRepository.findById(followId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_001));

        if (!follows.getFollowing().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.FOLLOW_004);
        }

        if (follows.getStatus() != Follow_state.PENDING) {
            throw new BusinessException(ErrorCode.FOLLOW_005);
        }

        followRepository.delete(follows);
    }

    // 내가 보낸 팔로우 요청 취소 또는 언팔로우
    public void cancelOrUnfollow(UUID followerId, UUID followingId) {

        Follows follows = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_001));

        followRepository.delete(follows);
    }

    @Transactional(readOnly = true)
    public UserFollowRequestPageResponse getFollowRequests(UUID userId, UUID cursor, Integer size) {
        int limit = normalizeSize(size);
        Pageable pageable = PageRequest.of(0, limit + 1);
        List<Follows> follows = cursor == null
            ? followRepository.findReceivedRequestsFirstPage(userId, Follow_state.PENDING, pageable)
            : followRepository.findReceivedRequestsAfterCursor(userId, Follow_state.PENDING, cursor, pageable);

        boolean hasNext = follows.size() == limit + 1;
        if (hasNext) {
            follows.remove(limit);
        }
        List<UserFollowRequestResponse> responses = new ArrayList<>();

        for (Follows follow : follows) {
            responses.add(UserFollowRequestResponse.from(follow));
        }

        UUID nextCursor = hasNext ? follows.get(follows.size() - 1).getId() : null;
        return new UserFollowRequestPageResponse(responses, nextCursor, hasNext);
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
