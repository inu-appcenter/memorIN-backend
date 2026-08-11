package com.memorin.domain.users.service;

import com.memorin.domain.users.dto.*;
import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    @Transactional(readOnly = true)
    public UserSearchPageResponse searchUsers(String keyword, UUID cursor, Integer size) {

        int limit = size == null ? 20 : size;
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<User> users = userRepository.searchUsersWithCursor(keyword, cursor, pageable);

        boolean hasNext = false;

        if (users.size() == limit + 1) {
            hasNext = true;
            users.remove(limit);
        }

        List<UserSearchResponse> items = new ArrayList<>();

        for (User user : users) {
            UserSearchResponse response = new UserSearchResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio()
            );

            items.add(response);
        }

        UUID nextCursor = null;

        if (hasNext && items.size() > 0) {
            nextCursor = items.get(items.size() - 1).id();
        }

        return new UserSearchPageResponse(items, nextCursor, hasNext);
    }

    public MyPageResponseDto getMyPage(UUID userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        return new MyPageResponseDto(
            user.getUsername(),
            user.getDisplayName(),
            user.getBio()
        );
    }

    @Transactional(readOnly = true)
    public UserFollowPageResponse getFollowers(UUID userId, UUID cursor, int size) {

        int limit = size;
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Follows> follows = followRepository.findFollowersWithCursor(
                userId,
                Follow_state.ACCEPTED,
                cursor,
                pageable
            );

        boolean hasNext = false;

        if (follows.size() == limit + 1) {
            hasNext = true;
            follows.remove(limit);
        }

        List<UserFollowResponse> items = new ArrayList<>();

        for (Follows follow : follows) {
            User user = follow.getFollower();
            UserFollowResponse response = UserFollowResponse.from(user);
            items.add(response);
        }

        UUID nextCursor = null;

        if (hasNext && items.size() > 0) {
            nextCursor = follows.get(follows.size() - 1).getId();
        }

        return new UserFollowPageResponse(
            items,
            nextCursor,
            hasNext
        );
    }

    @Transactional(readOnly = true)
    public UserFollowPageResponse getFollowings(UUID userId, UUID cursor, int size) {

        int limit = size;
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Follows> follows = followRepository.findFollowingsWithCursor(
                userId,
                Follow_state.ACCEPTED,
                cursor,
                pageable
            );

        boolean hasNext = false;

        if (follows.size() == limit + 1) {
            hasNext = true;
            follows.remove(limit);
        }

        List<UserFollowResponse> items = new ArrayList<>();

        for (Follows follow : follows) {
            User user = follow.getFollowing();
            UserFollowResponse response = UserFollowResponse.from(user);
            items.add(response);
        }

        UUID nextCursor = null;

        if (hasNext && items.size() > 0) {
            nextCursor = follows.get(follows.size() - 1).getId();
        }

        return new UserFollowPageResponse(
            items,
            nextCursor,
            hasNext
        );
    }
}
