package com.shopsphere.user.service;

import com.shopsphere.common.exception.ConflictException;
import com.shopsphere.common.exception.ResourceNotFoundException;
import com.shopsphere.common.exception.UnauthorizedException;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.user.dto.AuthResponse;
import com.shopsphere.user.dto.LoginRequest;
import com.shopsphere.user.dto.RefreshTokenRequest;
import com.shopsphere.user.dto.RegisterRequest;
import com.shopsphere.user.entity.RefreshToken;
import com.shopsphere.user.entity.Role;
import com.shopsphere.user.entity.User;
import com.shopsphere.user.repository.RefreshTokenRepository;
import com.shopsphere.user.repository.RoleRepository;
import com.shopsphere.user.repository.UserRepository;
import com.shopsphere.user.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ConflictException("User with email '" + request.getEmail() + "' already exists");
        }

        Role customerRole = roleRepository.findByName(SecurityConstants.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", SecurityConstants.ROLE_CUSTOMER));

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(request.getPhoneNumber())
                .active(true)
                .roles(new HashSet<>(Set.of(customerRole)))
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user with ID: {}", savedUser.getId());

        return createAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("User account is deactivated");
        }

        log.info("User logged in successfully: {}", user.getEmail());
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = request.getRefreshToken();
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            // Potential refresh token reuse attack! Revoke all tokens for this user.
            log.warn("Revoked refresh token reuse detected for user: {}", refreshToken.getUser().getId());
            refreshTokenRepository.revokeAllUserTokens(refreshToken.getUser());
            throw new UnauthorizedException("Revoked token reuse detected. All sessions terminated.");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new UnauthorizedException("Refresh token has expired. Please log in again.");
        }

        // Token rotation: Revoke existing token and issue a fresh one
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        return createAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshTokenString) {
        if (refreshTokenString != null && !refreshTokenString.isBlank()) {
            refreshTokenRepository.findByTokenHash(refreshTokenString)
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                        log.info("Revoked refresh token on logout for user: {}", token.getUser().getId());
                    });
        }
    }

    private AuthResponse createAuthResponse(User user) {
        String accessToken = jwtProvider.generateAccessToken(user);

        // Generate persistent refresh token
        String newRefreshToken = UUID.randomUUID().toString();
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(newRefreshToken)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getFirstName() + " " + user.getLastName())
                .roles(roles)
                .build();
    }
}
