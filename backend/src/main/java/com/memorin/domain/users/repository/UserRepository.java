package com.memorin.domain.users.repository;

import com.memorin.domain.users.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID userId);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email); //UNIQUE 설정

    boolean existsByUsername(String username);

    List<User> findByUsernameOrDisplayName(String username, String displayName);

    // Storage quota 예약(TOCTOU 방지)용 행 잠금. 같은 유저의 동시 presigned-upload-url 요청을
    // 직렬화해서 committed+pending 합산 후 예약 삽입까지 원자적으로 만든다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

    // 유저 검색(소셜 탐색 화면).
    //
    // 이전 버전 대비 바뀐 점 3가지:
    //  1) deleted_at IS NULL — 탈퇴한 유저가 검색 결과에 그대로 노출되고 있었다.
    //     users의 인덱스가 전부 WHERE deleted_at IS NULL 부분 인덱스인 것과도 어긋났다.
    //  2) LOWER(...) — Postgres LIKE는 대소문자를 구분한다. "Kim"으로 "kim"이 안 잡혔다.
    //  3) ESCAPE '\' — keyword의 %, _ 를 서비스에서 이스케이프한다.
    //     이스케이프가 없으면 검색어에 % 한 글자만 넣어도 전체 유저 목록이 나갔다.
    //
    // 참고: LIKE '%kw%' 는 선행 와일드카드라 어떤 B-tree 인덱스도 못 탄다.
    // 여기서 고친 건 성능이 아니라 정확성이다. 유저 수가 늘어 검색이 느려지면
    // pg_trgm GIN 인덱스나 전문검색으로 가야 하고, 그건 실측 후 별도로 판단한다.
    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
        AND (LOWER(u.username)    LIKE LOWER(:keyword) ESCAPE '\\'
          OR LOWER(u.displayName) LIKE LOWER(:keyword) ESCAPE '\\')
        ORDER BY u.id DESC
        """)
    List<User> searchUsersFirstPage(
        @Param("keyword") String keyword,
        Pageable pageable
    );

    // 커서 이후. FollowRepository와 같은 이유로 1페이지와 쿼리를 분리한다
    // (:cursorId IS NULL OR ... 로 합치면 커서가 Index Cond가 아니라 Filter로 밀린다).
    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
        AND (LOWER(u.username)    LIKE LOWER(:keyword) ESCAPE '\\'
          OR LOWER(u.displayName) LIKE LOWER(:keyword) ESCAPE '\\')
        AND u.id < :cursorId
        ORDER BY u.id DESC
        """)
    List<User> searchUsersAfterCursor(
        @Param("keyword") String keyword,
        @Param("cursorId") UUID cursorId,
        Pageable pageable
    );
}
