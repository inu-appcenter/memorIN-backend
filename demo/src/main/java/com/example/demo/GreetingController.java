package com.example.demo;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 뷰 대신 도메인 객체를 반환하는 컨트롤러 클래스
@RestController
public class GreetingController {

    private static final String template = "Hello, %s!";
    // 여러 요청이 동시에 들어올때 숫자 꼬이는 거 방지하는 안전한 카운터
    private final AtomicLong counter = new AtomicLong();

    // GetMapping 어노테이션으로 /greeting 경로로 greeting() 메소드를 전달해줌.
    @GetMapping("/greeting")
    // @RequestParam(defaultValue = "World") => URL에서 request 받는거임. world는 기본값.
    public Greeting greeting(@RequestParam(defaultValue = "World") String name) {
        return new Greeting(counter.incrementAndGet(), template.formatted(name));
    }
}
/*
* PostMapping => post를 위한 어노테이션
* RequestMapping ?
* RESTful 웹 서비스 컨트롤러와 기존 MVC 컨트롤러의 주요 차이점은 HTTP 응답 본문을 생성하는 방식
* 서버에서 데이터를 HTML로 렌더링하기 위해 뷰 기술에 의존 근데 RESTful 웹 서비스 컨트롤러는 객체를 생성하고 반환.
* 그래서 지금 이 예시에서 Greeting은 Json형식으로 HTTP 응답에 직접 기록됨.
* */