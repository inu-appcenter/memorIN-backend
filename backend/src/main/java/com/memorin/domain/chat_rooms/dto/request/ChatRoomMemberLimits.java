package com.memorin.domain.chat_rooms.dto.request;

/**
 * 채팅방 멤버 요청의 크기 상한.
 *
 * <p>{@code @Size(max = ...)}의 인자는 컴파일 타임 상수여야 해서 별도 상수로 뺐다.
 * 방 생성과 초대가 같은 값을 쓰도록 한곳에 둔다.
 */
public final class ChatRoomMemberLimits {

    /**
     * 요청 한 건에 담을 수 있는 멤버 수.
     *
     * <p>상한이 없으면 배열 크기만큼 {@code findById}가 반복된다. 온프레미스 저사양 서버가
     * 전제이므로 요청 하나가 DB를 수백 번 때리는 경로를 열어두지 않는다.
     */
    public static final int MAX_MEMBERS_PER_REQUEST = 100;

    private ChatRoomMemberLimits() {
    }
}
