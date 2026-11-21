package com.shopsphere.product.controller;

import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.product.dto.CategoryRequest;
import com.shopsphere.product.dto.CategoryResponse;
import com.shopsphere.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category browsing and management APIs")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a new product category (ADMIN only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestHeader(value = SecurityConstants.HEADER_USER_ROLES, required = false) String roles,
            @Valid @RequestBody CategoryRequest request) {

        // Validate ADMIN role forwarded from Gateway
        if (roles == null || !roles.contains(SecurityConstants.ROLE_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only administrators can create product categories"));
        }

        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all active categories (Public)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllActiveCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category details by ID (Public)")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable Long id) {
        CategoryResponse response = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
