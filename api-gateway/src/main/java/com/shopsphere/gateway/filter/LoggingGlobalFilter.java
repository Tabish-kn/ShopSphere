package com.shopsphere.gateway.filter;

import com.shopsphere.common.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global logging filter to record request methods, paths, and response latencies.
 */
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String correlationId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_CORRELATION_ID);

        log.info("[API-GATEWAY] Inbound: {} {} | Correlation-Id: {}", method, path, correlationId);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null ?
                    exchange.getResponse().getStatusCode().value() : 500;
            log.info("[API-GATEWAY] Outbound: {} {} -> HTTP {} (took {} ms) | Correlation-Id: {}",
                    method, path, statusCode, duration, correlationId);
        }));
    }

    @Override
    public int getOrder() {
        // Run after correlation filter but before routing
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
