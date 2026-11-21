package com.shopsphere.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shopsphere.common.dto.ErrorResponse;
import com.shopsphere.common.security.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Gateway perimeter security filter.
 * Validates JWT signatures and expiry, and injects authenticated identity claims (X-User-Id, X-User-Roles) downstream.
 */
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String jwtSecret;

    private final ObjectMapper objectMapper;

    public AuthenticationFilter() {
        super(Config.class);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public static class Config {
        // Configuration parameters if needed in routing rules
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // 1. Bypass public endpoints
            if (isPublicEndpoint(path, request.getMethod().name())) {
                return chain.filter(exchange);
            }

            // 2. Check for Authorization header
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
                return onError(exchange, "Invalid Authorization header format. Must start with 'Bearer '", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length()).trim();

            try {
                // 3. Validate Token and Extract Claims
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                String rolesString = roles != null ? String.join(",", roles) : "";

                // 4. Mutate request by injecting trusted security headers
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header(SecurityConstants.HEADER_USER_ID, userId)
                        .header(SecurityConstants.HEADER_USER_ROLES, rolesString)
                        .build();

                log.debug("Authenticated request for userId: {} with roles: {}", userId, rolesString);
                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (Exception ex) {
                log.warn("JWT validation failed for path {}: {}", path, ex.getMessage());
                return onError(exchange, "Unauthorized: " + ex.getMessage(), HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private boolean isPublicEndpoint(String path, String method) {
        if (path.startsWith("/api/v1/auth/") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/ws")) {
            return true;
        }
        // Product catalog browsing is public for GET requests
        return "GET".equalsIgnoreCase(method) &&
                (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/categories"));
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_CORRELATION_ID);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(httpStatus.value())
                .error(httpStatus.getReasonPhrase())
                .message(err)
                .path(exchange.getRequest().getURI().getPath())
                .correlationId(correlationId)
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
