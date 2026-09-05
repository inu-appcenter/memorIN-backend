package com.memorin.domain.chat_rooms.controller;

import com.memorin.domain.chat_rooms.dto.request.CreateDirectRoomRequest;
import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.dto.request.InviteMembersRequest;
import com.memorin.domain.chat_rooms.dto.request.RenameRoomRequest;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomResponse;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomSummaryResponse;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "채팅방", description = "채팅방 생성 및 수정, 멤버 관리")
@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    // TODO : 강톼당한 대상이 재입장이 가능 -> 현재 로직에서 강퇴를 '타인의 의한 나가기'로 정의되어 있음.

    @Operation(
        summary = "1:1 채팅방 생성",
        description = """
            자기자신과의 1:1 채팅방은 생성할 수 없음.
            1:1 채팅방이 존재하는 사람과는 이미 존재하는 경우 새롭게 만들 수 없음.
            존재하는 경우의 요청은 기존의 방을 응답하여 처리함.""")
    @PostMapping("/direct")
    public ChatRoomResponse createDirectRoom(@RequestBody CreateDirectRoomRequest request,
                                             @AuthenticationPrincipal UUID requesterId) {
        return chatRoomService.createDirectRoom(requesterId, request.targetUserId());
    }

    @Operation(
        summary = "그룹 채팅방 생성",
        description = """
            1:1과 유사한 로직
            대상을 찾을 수 없음(USER_001)이 존재하면 초대 로직 전체 실패 처리""")
    @PostMapping("/group")
    public ChatRoomResponse createGroupRoom(@RequestBody @Valid CreateGroupRoomRequest request,
                                            @AuthenticationPrincipal UUID requesterId) {
        return chatRoomService.createGroupRoom(requesterId, request);
    }

    @Operation(
        summary = "채팅방 조회",
        description = """
            본인이 들어가 있는 모든 채팅방을 조회""")
    @GetMapping
    public List<ChatRoomSummaryResponse> myRooms(@AuthenticationPrincipal UUID requesterId) {
        return chatRoomService.listMyRooms(requesterId);
    }

    @Operation(
        summary = "채팅방 초대",
        description = """
            원하는 대상을 해당 채팅방 일괄 초대""")
    @PostMapping("/{roomId}/members")
    public void inviteMembers(@PathVariable UUID roomId, @RequestBody @Valid InviteMembersRequest request,
                              @AuthenticationPrincipal UUID requesterId) {
        chatRoomService.inviteMembers(roomId, requesterId, request);
    }

    @Operation(
        summary = "채팅방 강퇴",
        description = """
            채팅방에 포함되어 있는 대상만 강퇴 할 수 있음.(CHAT_ROOM_MEMBERS_001)
            방장만 멤버를 강퇴할 수 있음.(CHAT_ROOMS_002)
            자기 자신 강퇴 불가능
            현재 로직에서 강퇴는 대상을 나가게만 함. 다시 못들어오게 하지는 못하는 상황(TODO, 추후에 수정 예정)""")
    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public void kickMember(@PathVariable UUID roomId, @PathVariable UUID targetUserId,
                           @AuthenticationPrincipal UUID requesterId) {
        chatRoomService.kickMember(roomId, requesterId, targetUserId);
    }

    @Operation(
        summary = "채팅방 나가기",
        description = """
            채팅방에서 자신을 제외시킴.""")
    @DeleteMapping("/{roomId}/members/me")
    public void leaveRoom(@PathVariable UUID roomId, @AuthenticationPrincipal UUID requesterId) {
        chatRoomService.leaveRoom(roomId, requesterId);
    }

    @Operation(
        summary = "채팅방 이름 변경",
        description = """
            방장만 채팅방의 이름을 변경할 수 있음.(CHAT_ROOMS_002)""")
    @PatchMapping("/{roomId}/name")
    public void renameRoom(@PathVariable UUID roomId, @RequestBody @Valid RenameRoomRequest request,
                           @AuthenticationPrincipal UUID requesterId) {
        chatRoomService.renameRoom(roomId, requesterId, request);
    }
}
