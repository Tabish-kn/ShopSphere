package com.shopsphere.gateway.filter;

import com.shopsphere.common.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter with highest precedence.
 * Injects a unique X-Correlation-Id if not present, and mirrors it into the downstream request and response.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(SecurityConstants.HEADER_CORRELATION_ID);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new Correlation-Id: {}", correlationId);
        }

        // Mutate request with correlation ID
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(SecurityConstants.HEADER_CORRELATION_ID, correlationId)
                .build();

        // Mutate response to echo correlation ID back to the client
        final String finalCorrelationId = correlationId;
        exchange.getResponse().getHeaders().add(SecurityConstants.HEADER_CORRELATION_ID, finalCorrelationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
