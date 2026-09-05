package com.memorin.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독(수신) 경로 prefix — MessageController에서 "/topic/rooms/{roomId}"로 보내는 것과 짝을 맞춤
        registry.enableSimpleBroker("/topic", "/queue");
        // @MessageMapping("/chat.sharePost")가 실제로는 "/app/chat.sharePost"로 노출됨
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // 운영 환경에서는 실제 프론트 도메인으로 좁혀주세요
            .withSockJS(); // 프론트에서 SockJS를 안 쓴다면 이 줄은 빼도 됩니다
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
