package com.memorin.member.repository;

import com.memorin.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email); //UNIQUE 설정

    boolean existsByUsername(String username);
}
