package com.memorin.domain.users;

import com.memorin.domain.users.dto.UserSearchPageResponse;
import com.memorin.domain.users.dto.UserSearchResponse;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.service.UserService;
import com.memorin.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 유저 검색(소셜 탐색 화면) 쿼리의 정확성을 고정한다.
// 검증하는 셋 다 이전 버전에서 실제로 틀렸던 동작이다.
//
// 주의: Postgres 컨테이너는 모든 테스트 클래스가 공유한다(PostgresTestSupport).
// 다른 클래스가 심어둔 users를 지우면 posts FK에 걸려 깨지므로, 전역 삭제 대신
// 고유 접두사(PREFIX)로 이 클래스의 데이터만 격리한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSearchTest extends PostgresTestSupport {

    private static final String PREFIX = "srchfixture";

    private static boolean seeded = false;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void seed() {
        if (seeded) return;

        tx.executeWithoutResult(status -> {
            // 대소문자가 섞인 username
            em.persist(new User(PREFIX + "-kim@memorin.test", "hash", PREFIX + "KimDoyoung", "김도영", null));
            // 닉네임(displayName)으로만 찾을 수 있는 유저
            em.persist(new User(PREFIX + "-lee@memorin.test", "hash", PREFIX + "lee", "이서윤" + PREFIX, null));
            // username에 밑줄이 실제로 들어간 유저
            em.persist(new User(PREFIX + "-park@memorin.test", "hash", PREFIX + "_100", "박태윤", null));
            // 위와 밑줄 자리만 다른 미끼. '_'를 와일드카드로 흘리면 이 유저까지 잡힌다.
            em.persist(new User(PREFIX + "-decoy@memorin.test", "hash", PREFIX + "X100", "미끼", null));

            // 탈퇴한 유저
            em.persist(new User(PREFIX + "-gone@memorin.test", "hash", PREFIX + "gone", "떠난사람", null));
            em.flush();

            em.createQuery("UPDATE User u SET u.deletedAt = :now WHERE u.username = :name")
                    .setParameter("now", LocalDateTime.now())
                    .setParameter("name", PREFIX + "gone")
                    .executeUpdate();
        });

        seeded = true;
    }

    private List<String> search(String keyword) {
        UserSearchPageResponse page = userService.searchUsers(keyword, null, 50);
        return page.items().stream().map(UserSearchResponse::username).toList();
    }

    @Test
    void 대소문자를_구분하지_않는다() {
        // Postgres LIKE는 대소문자를 구분한다. 소문자로 쳐도 "...KimDoyoung"이 잡혀야 한다.
        assertThat(search(PREFIX + "kimdoyoung")).contains(PREFIX + "KimDoyoung");
        assertThat(search(PREFIX + "KIMDOYOUNG")).contains(PREFIX + "KimDoyoung");
    }

    @Test
    void 탈퇴한_유저는_검색되지_않는다() {
        // deleted_at 필터가 없어 탈퇴 유저가 그대로 노출되던 동작을 막는다.
        assertThat(search(PREFIX))
                .contains(PREFIX + "KimDoyoung")
                .doesNotContain(PREFIX + "gone");
    }

    @Test
    void 퍼센트를_와일드카드가_아니라_검색어로_취급한다() {
        // 이스케이프가 없으면 검색창에 "%" 한 글자만 넣어도 전체 유저 목록이 나갔다.
        // %는 리터럴이어야 하고, username에 %를 가진 유저는 없으므로 결과가 비어야 한다.
        assertThat(search("%")).isEmpty();
    }

    @Test
    void 밑줄을_와일드카드가_아니라_검색어로_취급한다() {
        // "_100"으로 검색하면 실제로 밑줄을 가진 유저만 나와야 한다.
        // 이스케이프가 없으면 '_'가 "아무 글자 하나"가 되어 미끼(...X100)까지 잡힌다.
        assertThat(search(PREFIX + "_100"))
                .contains(PREFIX + "_100")
                .doesNotContain(PREFIX + "X100");
    }

    @Test
    void 닉네임으로도_검색된다() {
        assertThat(search("이서윤" + PREFIX)).contains(PREFIX + "lee");
    }
}
