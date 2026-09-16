package com.memorin.domain.chat_rooms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record InviteMembersRequest(

    // 상한이 없으면 배열 크기만큼 findById가 반복된다. 한 번에 초대할 수 있는 인원을 제한한다.
    @NotEmpty
    @Size(max = ChatRoomMemberLimits.MAX_MEMBERS_PER_REQUEST,
        message = "한 번에 최대 " + ChatRoomMemberLimits.MAX_MEMBERS_PER_REQUEST + "명까지 초대할 수 있습니다.")
    List<@NotNull UUID> memberIds

) {
}
