package com.memorin.domain.users.repository;

import com.memorin.domain.users.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email); //UNIQUE 설정

    boolean existsByUsername(String username);

    // Storage quota 예약(TOCTOU 방지)용 행 잠금. 같은 유저의 동시 presigned-upload-url 요청을
    // 직렬화해서 committed+pending 합산 후 예약 삽입까지 원자적으로 만든다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}