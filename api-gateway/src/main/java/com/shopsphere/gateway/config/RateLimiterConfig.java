package com.shopsphere.gateway.config;

import com.shopsphere.common.security.SecurityConstants;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Redis Rate Limiter KeyResolver configuration.
 * Resolves client key using authenticated userId when available, otherwise falls back to client IP address.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            // First check if user ID was injected or passed in header
            String userId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            // Fallback to client remote IP address
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just("ip:" + remoteAddress.getAddress().getHostAddress());
            }

            return Mono.just("ip:anonymous");
        };
    }
}
