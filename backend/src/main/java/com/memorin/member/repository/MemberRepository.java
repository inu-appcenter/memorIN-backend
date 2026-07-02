package com.memorin.member.repository;

import com.memorin.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email); //UNIQUE 설정

    boolean existsByUsername(String username);
}
