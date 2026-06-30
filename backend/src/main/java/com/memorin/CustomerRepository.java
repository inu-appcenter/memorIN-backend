package com.memorin;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

// Customer 테이블, PK는 Long 타입
public interface CustomerRepository extends CrudRepository<Customer, Long> {
    List<Customer> findByFirstName(String firstName); // select문
    Customer findById(long id);
}
/*
* 원래 같으면, sql문에 따라 각각의 클래스가 필요한데, JPA를 쓰면 CrudRepository 하나로 끝
*
* */