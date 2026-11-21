package com.shopsphere.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring WebSocket & STOMP Message Broker Configuration.
 * Endpoint: /ws
 * Subscriptions: /topic/orders/{orderId}, /user/queue/notifications
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSecurityInterceptor securityInterceptor;

    public WebSocketConfig(WebSocketSecurityInterceptor securityInterceptor) {
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable in-memory simple broker for broadcasting to /topic and private /queue
        config.enableSimpleBroker("/topic", "/queue");
        // Application destination prefix for client-to-server messages
        config.setApplicationDestinationPrefixes("/app");
        // Prefix used for user-specific private destination routing
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Expose WebSocket connection endpoint with SockJS fallback support
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Intercept STOMP frames to authenticate users via JWT
        registration.interceptors(securityInterceptor);
    }
}
