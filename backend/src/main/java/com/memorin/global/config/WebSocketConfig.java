package com.memorin.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

// STOMP 브로커 설정.
//
// InMemory 브로커(enableSimpleBroker)는 구독 정보와 미전송 메시지를 전부 JVM 힙에 들고 있다.
// 외부 브로커가 없으므로 새는 곳은 전부 우리 힙이다. 여기 걸린 제한들은 그걸 막기 위한 것이고,
// 근거와 대안 검토는 docs/sprint4-architecture-review.md §3·§8에 있다.
//
// 숫자(10초·512KB·64KB·30초)는 출발점이지 정답이 아니다. W9 스트레스 테스트(§6) 실측 후 조정한다.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long[] HEARTBEAT_INTERVAL_MS = {10_000, 10_000};   // 서버→클라 / 클라→서버

    private final List<String> allowedOrigins;

    // CorsConfig와 같은 프로퍼티를 쓴다. 콤마로 구분된 문자열이 List<String>으로 자동 변환된다.
    // WebSocket 핸드셰이크의 Origin 검사는 Spring Security의 CORS 설정과 별개로 동작하기 때문에,
    // 여기를 "*"로 열어두면 Sprint 1에서 나눠 놓은 dev/prod 오리진 구분이 무의미해진다. (§8)
    public WebSocketConfig(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    // 하트비트 전용 스케줄러. 하트비트만 켜고 이 스케줄러를 안 주면 기동 시 실패한다.
    @Bean
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독(수신) 경로 prefix — MessageController에서 "/topic/rooms/{roomId}"로 보내는 것과 짝을 맞춤
        //
        // 하트비트는 죽은 연결을 서버가 스스로 알아채는 유일한 수단이다. 탭 강제 종료나 네트워크 단절로
        // DISCONNECT 없이 사라진 클라이언트는 이게 없으면 영원히 살아 있는 세션으로 남는다.
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(HEARTBEAT_INTERVAL_MS)
            .setTaskScheduler(webSocketHeartbeatScheduler());
        // @MessageMapping("/chat.sharePost")가 실제로는 "/app/chat.sharePost"로 노출됨
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
            .withSockJS(); // 프론트에서 SockJS를 안 쓴다면 이 줄은 빼도 됩니다
    }

    // 느린 수신자와 큰 프레임이 힙을 먹는 것을 막는다.
    // 제한이 없으면 보내지 못한 메시지가 세션 버퍼에 무한정 쌓이고, 프레임 하나가 힙을 크게 먹는다.
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setSendTimeLimit(10 * 1000)          // 10초 안에 못 보내면 세션을 끊는다
            .setSendBufferSizeLimit(512 * 1024)       // 세션당 미전송 버퍼 상한
            .setMessageSizeLimit(64 * 1024)           // 인바운드 프레임 크기 상한
            .setTimeToFirstMessage(30 * 1000);        // 연결만 하고 CONNECT를 안 보내는 세션 정리
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        // 이게 없으면 @MessageMapping 핸들러의 @AuthenticationPrincipal이 인식되지 않고
        // 페이로드 바인딩으로 fallback되어 principal 대신 빈 객체가 주입된다.
        argumentResolvers.add(new AuthenticationPrincipalArgumentResolver());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 핸드셰이크 시점의 Authentication을 메시지 처리 스레드의 SecurityContext로 옮겨줘야
        // AuthenticationPrincipalArgumentResolver가 찾을 수 있다 (STOMP 메시지는 핸드셰이크와
        // 다른 스레드에서 처리되므로 SecurityContextHolder가 비어있는 상태로 시작한다).
        registration.interceptors(new SecurityContextChannelInterceptor());
    }
}
