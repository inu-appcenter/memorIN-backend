package com.memorin;

public record Greeting(long id, String content) {
}
/* record로 한줄로 줄인거임. 근데 불변 객체라 단순 데이터 전달용
* public class Greeting {
    private final long id;
    private final String content;

    public Greeting(long id, String content) {
        this.id = id;
        this.content = content;
    }

    public long getId() { return id; }
    public String getContent() { return content; }
}
* */