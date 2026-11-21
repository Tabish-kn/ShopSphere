package com.shopsphere.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationFilterTest {

    private AuthenticationFilter authenticationFilter;
    private final String secretString = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        authenticationFilter = new AuthenticationFilter();
        ReflectionTestUtils.setField(authenticationFilter, "jwtSecret", secretString);
    }

    @Test
    @DisplayName("Should successfully parse valid JWT and extract userId and roles")
    void shouldValidateValidJwtToken() {
        UUID userId = UUID.randomUUID();
        SecretKey key = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("email", "john.doe@example.com")
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();

        assertNotNull(token);
        assertFalse(token.isBlank());

        // Validate parsing
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(userId.toString(), claims.getSubject());
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertEquals(1, roles.size());
        assertEquals("ROLE_CUSTOMER", roles.get(0));
    }
}
