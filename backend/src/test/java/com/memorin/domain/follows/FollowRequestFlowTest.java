package com.memorin.domain.follows;

import com.memorin.domain.follows.entity.Follow_state;
import com.memorin.domain.follows.entity.Follows;
import com.memorin.domain.follows.repository.FollowRepository;
import com.memorin.domain.follows.service.FollowService;
import com.memorin.domain.users.dto.UserFollowRequestResponse;
import com.memorin.domain.users.entity.User;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 팔로우 요청의 수락 · 거절 · 취소를 실행 경로로 고정한다.
//
// 왜 필요한가: 이 셋은 **서로 다른 식별자**를 쓴다.
//   수락 · 거절 → followId    (팔로우 행의 id)
//   취소/언팔로우 → followingId (상대 사용자의 id)
// 이걸 헷갈려 거절이 반대 방향을 조회하는 바람에 "받은 요청 거절이 항상 404"였던 적이 있다(#163).
// 목록은 (follower=상대, following=나)를 돌려주는데 거절은 (follower=나, following=상대)를 찾았다.
//
// 기존 팔로우 테스트는 목록 페이징만 검증한다. 상태를 바꾸는 경로에는 테스트가 없어
// 그 결함이 머지에서 걸러지지 않았다. 여기서 각 경로가 "어느 방향"을 다루는지까지 못 박는다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FollowRequestFlowTest extends PostgresTestSupport {

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    // requester가 receiver에게 보낸 PENDING 요청 하나.
    private record Request(UUID requesterId, UUID receiverId, UUID followId) {}

    private Request seedPendingRequest(String tag) {
        return tx.execute(status -> {
            User requester = newUser(tag + "-req");
            User receiver = newUser(tag + "-recv");
            Follows follows = new Follows(requester, receiver);
            em.persist(follows);
            em.flush();
            return new Request(requester.getId(), receiver.getId(), follows.getId());
        });
    }

    private User newUser(String tag) {
        User user = new User(tag + "@memorin.test", "hash", tag, tag, null);
        em.persist(user);
        return user;
    }

    private Follow_state statusOf(UUID followId) {
        return followRepository.findById(followId).orElseThrow().getStatus();
    }

    // ---- 거절 ----

    @Test
    void 받은_요청은_목록이_준_followId로_거절된다() {
        Request request = seedPendingRequest("reject-ok");

        // 목록이 내려주는 followId를 그대로 쓰는 것이 API 계약이다.
        List<UserFollowRequestResponse> received = followService.getFollowRequests(request.receiverId());
        UUID followId = received.stream()
                .filter(r -> r.userId().equals(request.requesterId()))
                .findFirst().orElseThrow().followId();
        assertThat(followId).isEqualTo(request.followId());

        followService.reject(followId, request.receiverId());

        assertThat(followRepository.findById(followId))
                .as("거절하면 요청 행이 삭제된다")
                .isEmpty();
    }

    @Test
    void 내게_온_요청이_아니면_거절할_수_없다() {
        Request request = seedPendingRequest("reject-other");
        UUID outsiderId = tx.execute(s -> {
            UUID id = newUser("reject-outsider").getId();
            em.flush();
            return id;
        });

        assertThatThrownBy(() -> followService.reject(request.followId(), outsiderId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_004);

        assertThat(followRepository.findById(request.followId())).isPresent();
    }

    @Test
    void 이미_수락된_관계는_거절_대상이_아니다() {
        Request request = seedPendingRequest("reject-accepted");
        followService.accept(request.followId(), request.receiverId());

        // 관계 해제는 거절이 아니라 취소/언팔로우 경로(§9-2)의 일이다.
        assertThatThrownBy(() -> followService.reject(request.followId(), request.receiverId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_001);

        assertThat(statusOf(request.followId())).isEqualTo(Follow_state.ACCEPTED);
    }

    // ---- 취소 / 언팔로우 ----

    // #163 회귀 방지의 핵심. 두 경로는 반대 방향을 다루므로 서로를 대신할 수 없다.
    @Test
    void 받은_요청은_취소_경로로는_지워지지_않는다() {
        Request request = seedPendingRequest("cancel-wrong-way");

        // 받은 요청은 (follower=상대, following=나)다.
        // 취소 경로는 (follower=나, following=상대)를 찾으므로 해당 행이 없다.
        assertThatThrownBy(() -> followService.cancelOrUnfollow(request.receiverId(), request.requesterId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_001);

        assertThat(followRepository.findById(request.followId()))
                .as("취소 경로가 받은 요청을 지워버리면 안 된다")
                .isPresent();
    }

    @Test
    void 내가_보낸_요청은_취소_경로로_지워진다() {
        Request request = seedPendingRequest("cancel-ok");

        followService.cancelOrUnfollow(request.requesterId(), request.receiverId());

        assertThat(followRepository.findById(request.followId())).isEmpty();
    }

    @Test
    void 수락된_관계도_같은_경로로_해제된다() {
        Request request = seedPendingRequest("unfollow-ok");
        followService.accept(request.followId(), request.receiverId());

        followService.cancelOrUnfollow(request.requesterId(), request.receiverId());

        assertThat(followRepository.findById(request.followId())).isEmpty();
    }

    // ---- 수락 ----

    @Test
    void 받은_요청은_followId로_수락되고_ACCEPTED가_된다() {
        Request request = seedPendingRequest("accept-ok");

        followService.accept(request.followId(), request.receiverId());

        assertThat(statusOf(request.followId())).isEqualTo(Follow_state.ACCEPTED);
    }

    @Test
    void 요청을_보낸_사람이_자기_요청을_수락할_수_없다() {
        Request request = seedPendingRequest("accept-self");

        assertThatThrownBy(() -> followService.accept(request.followId(), request.requesterId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_004);

        assertThat(statusOf(request.followId())).isEqualTo(Follow_state.PENDING);
    }
}
