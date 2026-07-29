package com.memorin.domain.users.service;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.users.dto.UserFollowPageResponse;
import com.memorin.domain.users.dto.UserFollowResponse;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.dto.UserSearchResponse;
import com.memorin.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    public List<UserSearchResponse> searchUsers(String keyword) {

        List<User> users = userRepository.findByUsernameOrDisplayName(keyword, keyword);
        List<UserSearchResponse> response = new ArrayList<>();

        for (User user : users) {
            UserSearchResponse userResponse = new UserSearchResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getBio()
            );

            response.add(userResponse);
        }

        return response;
    }

    public UserFollowPageResponse getFollowers(UUID userId, UUID cursor, int size) {

        List<Follows> follows = followRepository.findByFollowingIdAndStatus(userId, Follow_state.ACCEPTED);
        List<UserFollowResponse> items = new ArrayList<>();

        for (Follows follow : follows) {
            User user = follow.getFollower();
            UserFollowResponse response = UserFollowResponse.from(user);
            items.add(response);
        }

        boolean hasNext = false;

        if (items.size() == size + 1) {
            hasNext = true;
        }

        UUID nextCursor = null;

        if (hasNext) {
            UserFollowResponse lastUser = items.get(items.size() - 1);
            nextCursor = lastUser.id();
            items.remove(items.size() - 1);
        }

        return new UserFollowPageResponse(
            items,
            nextCursor,
            hasNext
        );
    }

    public UserFollowPageResponse getFollowings(UUID userId, UUID cursor, int size) {

        List<Follows> follows = followRepository.findByFollowerIdAndStatus(userId, Follow_state.ACCEPTED);
        List<UserFollowResponse> items = new ArrayList<>();

        for (Follows follow : follows) {
            User user = follow.getFollowing();
            UserFollowResponse response = UserFollowResponse.from(user);
            items.add(response);
        }

        boolean hasNext = false;

        if (items.size() == size + 1) {
            hasNext = true;
        }

        UUID nextCursor = null;

        if (hasNext) {
            UserFollowResponse lastUser = items.get(items.size() - 1);
            nextCursor = lastUser.id();
            items.remove(items.size() - 1);
        }

        return new UserFollowPageResponse(
            items,
            nextCursor,
            hasNext
        );
    }
}
