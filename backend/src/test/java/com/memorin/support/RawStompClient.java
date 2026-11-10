package com.memorin.support;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * STOMP 프레임을 손으로 써서 보내는 테스트용 클라이언트.
 *
 * <p>{@code WebSocketStompClient}는 정상 클라이언트라 {@code STOMP} 연결 프레임이나 브로커 목적지로의
 * 직접 SEND처럼 공격자가 보내는 프레임을 만들 수 없고, 서버가 보낸 ERROR 프레임도 감춘다.
 * 그래서 우회 시나리오(#304)는 프레임을 그대로 주고받는 이 클라이언트로 확인한다.
 */
public final class RawStompClient implements AutoCloseable {

    public record Frame(String command, Map<String, String> headers, String body) {
        public String header(String name) {
            return headers.get(name);
        }
    }

    private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    private final CountDownLatch closed = new CountDownLatch(1);
    private volatile CloseStatus closeStatus;
    private final WebSocketSession session;

    private RawStompClient(String url) throws Exception {
        this.session = new StandardWebSocketClient()
            .execute(new TextWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    String payload = message.getPayload();
                    // 하트비트는 줄바꿈 하나다. 프레임이 아니므로 버린다.
                    if (payload.isBlank()) {
                        return;
                    }
                    frames.add(parse(payload));
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                    closeStatus = status;
                    closed.countDown();
                }
            }, url)
            .get(10, TimeUnit.SECONDS);
    }

    /** SockJS 엔드포인트의 순수 WebSocket 경로({@code /ws/websocket})로 붙는다. 아직 STOMP 연결 프레임은 보내지 않는다. */
    public static RawStompClient open(int port) throws Exception {
        return new RawStompClient("ws://127.0.0.1:" + port + "/ws/websocket");
    }

    /** 연결 프레임을 보내고 서버의 첫 응답 프레임(CONNECTED 또는 ERROR)을 돌려준다. */
    public Frame connect(String command, String bearerTokenOrNull) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("accept-version", "1.2");
        headers.put("host", "localhost");
        headers.put("heart-beat", "0,0");
        if (bearerTokenOrNull != null) {
            headers.put("Authorization", "Bearer " + bearerTokenOrNull);
        }
        send(command, headers, "");
        return next(Duration.ofSeconds(5));
    }

    public void subscribe(String id, String destination) throws Exception {
        send("SUBSCRIBE", Map.of("id", id, "destination", destination), "");
    }

    public void sendJson(String destination, String json) throws Exception {
        send("SEND", Map.of("destination", destination, "content-type", "application/json"), json);
    }

    public void send(String command, Map<String, String> headers, String body) throws Exception {
        StringBuilder frame = new StringBuilder(command).append('\n');
        headers.forEach((k, v) -> frame.append(k).append(':').append(v).append('\n'));
        frame.append('\n').append(body).append('\0');
        synchronized (session) {
            session.sendMessage(new TextMessage(frame.toString()));
        }
    }

    /** 다음 프레임. 시간 안에 안 오면 {@code null}. */
    public Frame next(Duration timeout) throws InterruptedException {
        return frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 주어진 시간 동안 들어온 프레임 중 해당 명령의 첫 프레임. */
    public Frame nextOf(String command, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return null;
            }
            Frame frame = frames.poll(remaining, TimeUnit.NANOSECONDS);
            if (frame == null) {
                return null;
            }
            if (frame.command().equals(command)) {
                return frame;
            }
        }
    }

    public boolean awaitClosed(Duration timeout) throws InterruptedException {
        return closed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public CloseStatus closeStatus() {
        return closeStatus;
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close() {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // 이미 끊긴 세션
        }
    }

    private static Frame parse(String payload) {
        String raw = payload.endsWith("\0") ? payload.substring(0, payload.length() - 1) : payload;
        int headerEnd = raw.indexOf("\n\n");
        String head = headerEnd < 0 ? raw : raw.substring(0, headerEnd);
        String body = headerEnd < 0 ? "" : raw.substring(headerEnd + 2);
        String[] lines = head.split("\n");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.putIfAbsent(lines[i].substring(0, colon), lines[i].substring(colon + 1));
            }
        }
        return new Frame(lines[0].trim(), headers, body);
    }
}
