package com.shopsphere.user.service;

import com.shopsphere.common.exception.ConflictException;
import com.shopsphere.common.exception.UnauthorizedException;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.user.dto.AuthResponse;
import com.shopsphere.user.dto.LoginRequest;
import com.shopsphere.user.dto.RegisterRequest;
import com.shopsphere.user.entity.Role;
import com.shopsphere.user.entity.User;
import com.shopsphere.user.repository.RefreshTokenRepository;
import com.shopsphere.user.repository.RoleRepository;
import com.shopsphere.user.repository.UserRepository;
import com.shopsphere.user.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    private Role customerRole;
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604800000L);

        customerRole = Role.builder()
                .id(1)
                .name(SecurityConstants.ROLE_CUSTOMER)
                .build();

        user = User.builder()
                .id(UUID.randomUUID())
                .email("alex@example.com")
                .passwordHash("encoded-password")
                .firstName("Alex")
                .lastName("Taylor")
                .active(true)
                .roles(Set.of(customerRole))
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new user")
    void shouldRegisterNewUser() {
        RegisterRequest request = RegisterRequest.builder()
                .email("alex@example.com")
                .password("Password@123")
                .firstName("Alex")
                .lastName("Taylor")
                .phoneNumber("+1234567890")
                .build();

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(roleRepository.findByName(SecurityConstants.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtProvider.generateAccessToken(any(User.class))).thenReturn("mock-access-token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("alex@example.com", response.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
        verify(refreshTokenRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should throw ConflictException when registering with duplicate email")
    void shouldThrowConflictWhenEmailExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("alex@example.com")
                .password("Password@123")
                .firstName("Alex")
                .lastName("Taylor")
                .build();

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully log in with valid credentials")
    void shouldLoginSuccessfully() {
        LoginRequest request = LoginRequest.builder()
                .email("alex@example.com")
                .password("Password@123")
                .build();

        when(userRepository.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "encoded-password")).thenReturn(true);
        when(jwtProvider.generateAccessToken(user)).thenReturn("valid-jwt");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("valid-jwt", response.getAccessToken());
        assertEquals(user.getId(), response.getUserId());
    }

    @Test
    @DisplayName("Should throw UnauthorizedException with incorrect password")
    void shouldFailLoginWithWrongPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("alex@example.com")
                .password("WrongPassword")
                .build();

        when(userRepository.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "encoded-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }
}
