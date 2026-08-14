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

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    // PostService.normalizeSize와 같은 규칙. 클라이언트가 size=100000을 보내면 그대로
    // PageRequest에 실려 100만 행을 로드하고, size=-1이면 PageRequest.of가 예외를 던져 500이 된다.
    private int normalizeSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    // 검색어를 LIKE 패턴으로 바꾼다.
    // 사용자가 입력한 %, _ 는 LIKE의 와일드카드라 그대로 넘기면 검색어가 아니라 패턴이 된다.
    // (검색창에 "%" 한 글자만 쳐도 전체 유저가 나갔다.)
    // 백슬래시를 먼저 이스케이프해야 한다 — 나중에 하면 앞에서 붙인 이스케이프 문자까지 다시 escape된다.
    private String toLikePattern(String keyword) {
        String escaped = keyword
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");

        return "%" + escaped + "%";
    }

    @Transactional(readOnly = true)
    public UserSearchPageResponse searchUsers(String keyword, UUID cursor, Integer size) {

        int limit = normalizeSize(size);
        Pageable pageable = PageRequest.of(0, limit + 1);

        String pattern = toLikePattern(keyword);

        List<User> users = cursor == null
            ? userRepository.searchUsersFirstPage(pattern, pageable)
            : userRepository.searchUsersAfterCursor(pattern, cursor, pageable);

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

        int limit = normalizeSize(size);
        Pageable pageable = PageRequest.of(0, limit + 1);

        // 커서 유무로 쿼리를 갈라 쓴다. 이유는 FollowRepository 주석 참고
        // (하나로 합쳐 :cursor IS NULL OR ... 로 쓰면 커서가 Index Cond가 아니라 Filter로 밀린다).
        List<Follows> follows = cursor == null
            ? followRepository.findFollowersFirstPage(userId, Follow_state.ACCEPTED, pageable)
            : followRepository.findFollowersAfterCursor(userId, Follow_state.ACCEPTED, cursor, pageable);

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

        int limit = normalizeSize(size);
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Follows> follows = cursor == null
            ? followRepository.findFollowingsFirstPage(userId, Follow_state.ACCEPTED, pageable)
            : followRepository.findFollowingsAfterCursor(userId, Follow_state.ACCEPTED, cursor, pageable);

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

    @Transactional(readOnly = true)
    public UserProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        return UserProfileResponse.from(user);
    }
}
