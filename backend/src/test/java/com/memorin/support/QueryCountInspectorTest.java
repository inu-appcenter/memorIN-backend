package com.memorin.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// #290: 다른 스레드(스케줄 잡, @Async 리스너)의 SQL이 측정값에 섞이지 않아야 한다.
class QueryCountInspectorTest {

    private final QueryCountInspector inspector = new QueryCountInspector();

    @Test
    void 현재_스레드의_SQL만_센다() throws InterruptedException {
        QueryCountInspector.reset();

        inspector.inspect("select 1");
        Thread background = new Thread(() -> {
            inspector.inspect("select 2");
            inspector.inspect("select 3");
        });
        background.start();
        background.join();
        inspector.inspect("select 4");

        assertThat(QueryCountInspector.count()).isEqualTo(2);
    }

    @Test
    void reset하면_0부터_다시_센다() {
        inspector.inspect("select 1");

        QueryCountInspector.reset();
        inspector.inspect("select 2");

        assertThat(QueryCountInspector.count()).isEqualTo(1);
    }
}
