package com.memorin.domain.users.service;

import com.memorin.domain.users.dto.MyPageResponseDto;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.dto.UserSearchResponse;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
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

    public MyPageResponseDto getMyPage(UUID userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        return new MyPageResponseDto(
            user.getUsername(),
            user.getDisplayName(),
            user.getBio()
        );
    }
}
