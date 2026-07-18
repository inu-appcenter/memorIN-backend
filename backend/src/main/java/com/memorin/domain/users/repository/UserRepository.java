package com.memorin.domain.users.repository;

import com.memorin.domain.users.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email); //UNIQUE 설정

    boolean existsByUsername(String username);
}