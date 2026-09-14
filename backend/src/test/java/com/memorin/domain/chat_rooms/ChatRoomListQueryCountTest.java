package com.memorin.domain.chat_rooms;

import com.memorin.domain.chat_room_members.entity.ChatRoomMembers;
import com.memorin.domain.chat_rooms.dto.response.ChatRoomSummaryResponse;
import com.memorin.domain.chat_rooms.entity.ChatRooms;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.domain.users.entity.User;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 내 채팅방 목록 조회가 방 개수에 비례해 SQL을 늘리지 않는지(N+1 없음) 실측한다.
//
// listMyRooms는 멤버 행을 가져온 뒤 m.getRoom()으로 방 이름·타입을 읽는다. room이 LAZY라
// fetch 전략이 빠지면 방 1개당 SELECT가 1번씩 더 나간다. 채팅 첫 화면이라 방이 20개면
// 쿼리도 21개가 되고 그대로 체감된다(docs/sprint4-code-review.md §11-3).
//
// 측정 방식과 주의사항은 FollowListQueryCountTest와 같다 —
// 데이터를 심는 트랜잭션과 측정 트랜잭션을 분리해야 1차 캐시가 조회를 가로채지 않는다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRoomListQueryCountTest extends PostgresTestSupport {

    @DynamicPropertySource
    static void enableStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private Statistics statistics() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    // roomCount개의 그룹 방을 만들고 전부 owner로 참여시킨다. 그 사용자의 id를 반환.
    private UUID seedRooms(String tag, int roomCount) {
        return tx.execute(status -> {
            User owner = new User(tag + "@memorin.test", "hash", tag, tag, null);
            em.persist(owner);
            for (int i = 0; i < roomCount; i++) {
                ChatRooms room = ChatRooms.createGroup("%s-room-%d".formatted(tag, i));
                em.persist(room);
                em.persist(ChatRoomMembers.ofOwner(room, owner));
            }
            em.flush();
            return owner.getId();
        });
    }

    private long countQueriesForRooms(UUID userId, int expected) {
        Statistics stats = statistics();
        stats.clear();

        List<ChatRoomSummaryResponse> rooms = chatRoomService.listMyRooms(userId);

        // 방 이름까지 실제로 읽혔는지 확인한다. 이름을 안 읽으면 LAZY 프록시가 초기화되지
        // 않아 N+1이 있어도 이 테스트가 통과해 버린다.
        assertThat(rooms).hasSize(expected);
        assertThat(rooms).allSatisfy(r -> assertThat(r.name()).isNotBlank());

        return stats.getPrepareStatementCount();
    }

    @Test
    void 채팅방_개수를_늘려도_쿼리는_늘지_않는다() {
        // given — 방 개수만 3배 차이
        String few = "few" + UUID.randomUUID().toString().substring(0, 6);
        String many = "many" + UUID.randomUUID().toString().substring(0, 6);
        UUID fewUser = seedRooms(few, 3);
        UUID manyUser = seedRooms(many, 9);

        // when
        long fewQueries = countQueriesForRooms(fewUser, 3);
        long manyQueries = countQueriesForRooms(manyUser, 9);

        System.out.printf("%n>>> 채팅방 3개 → SQL %d개%n", fewQueries);
        System.out.printf(">>> 채팅방 9개 → SQL %d개%n%n", manyQueries);

        // 방이 3배 늘었는데 쿼리도 따라 늘면 N+1이다.
        assertThat(manyQueries)
            .as("방 개수에 비례해 쿼리가 늘어나면 N+1")
            .isEqualTo(fewQueries);
    }
}
