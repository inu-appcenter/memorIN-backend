package com.memorin.domain.users.dto;

import com.memorin.domain.users.entity.User;

import java.util.UUID;

public record UserFollowResponse(

    UUID id,
    String username,
    String displayName,
    // 스토리지 키. 화면에 그릴 때는 profileImageUrl을 쓴다. 기존 클라이언트 호환을 위해 남겨 둔다.
    String profileImageKey,
    // presigned 다운로드 URL. 프로필 이미지가 없거나 URL 발급에 실패하면 null(#347).
    String profileImageUrl

) {
    public static UserFollowResponse from(User user, String profileImageUrl) {

        return new UserFollowResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getProfileImageKey(),
            profileImageUrl
        );
    }
}
