package com.memorin.domain.auth.event;

import java.util.UUID;

/**
 * 이 사용자의 열린 실시간 연결(웹소켓 세션)을 모두 끊으라는 신호(#304). 로그아웃·회원 탈퇴에서 발행한다.
 *
 * <p>STOMP 세션은 연결할 때 한 번만 인증한다. 그 뒤로는 토큰이 폐기돼도 연결이 살아 있으므로,
 * 끊지 않으면 로그아웃·탈퇴한 기기로 계속 메시지가 간다. 듣는 쪽은 커밋 이후에만 반응한다 —
 * 롤백된 로그아웃으로 연결을 끊지 않기 위해서다.
 */
public record UserSessionsRevoked(UUID userId) {
}
