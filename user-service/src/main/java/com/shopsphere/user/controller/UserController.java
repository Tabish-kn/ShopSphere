package com.shopsphere.user.controller;

import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.user.dto.UpdateProfileRequest;
import com.shopsphere.user.dto.UserProfileResponse;
import com.shopsphere.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile retrieval and management")
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Retrieve authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId) {
        UserProfileResponse response = userService.getUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    @Operation(summary = "Update authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @Operation(summary = "Get user profile by ID (Admin or Self)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(@PathVariable UUID id) {
        UserProfileResponse response = userService.getUserProfile(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
