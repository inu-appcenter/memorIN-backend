package com.memorin.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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
}
