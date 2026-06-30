package com.memorin;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

// @Table 어노테이션은 존재하지 않는다.
@Entity
public class Customer {

    // @Id, @GeneratedValue를 같이 써서 id 자동으로 생성되게 하고 해당 id는 각 값을 가리키도록 함.
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String firstName;
    private String lastName;

    // id, firstName, lastName 이런거를 바로 쓰지 않고 protected로 디자인하여 사용
    protected Customer() {}

    // 생성자로 db를 save함.
    public Customer(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public String toString() {
        return String.format(
                "Customer[id=%d, firstName='%s', lastName='%s']",
                id, firstName, lastName);
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}