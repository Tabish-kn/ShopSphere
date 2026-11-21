package com.shopsphere.product.controller;

import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.dto.PageResponse;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.product.dto.ProductCreateRequest;
import com.shopsphere.product.dto.ProductResponse;
import com.shopsphere.product.dto.ProductUpdateRequest;
import com.shopsphere.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog browsing, search, and admin management APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product (ADMIN only)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestHeader(value = SecurityConstants.HEADER_USER_ROLES, required = false) String roles,
            @Valid @RequestBody ProductCreateRequest request) {

        if (roles == null || !roles.contains(SecurityConstants.ROLE_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only administrators can create products"));
        }

        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product details by ID (Public, Cached in Redis)")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable UUID id) {
        ProductResponse response = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Search and filter products (Public, Paginated & Sorted)")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = "desc".equalsIgnoreCase(direction) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PageResponse<ProductResponse> response = productService.searchProducts(query, categoryId, active, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing product (ADMIN only)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @RequestHeader(value = SecurityConstants.HEADER_USER_ROLES, required = false) String roles,
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpdateRequest request) {

        if (roles == null || !roles.contains(SecurityConstants.ROLE_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only administrators can update products"));
        }

        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete / deactivate a product (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @RequestHeader(value = SecurityConstants.HEADER_USER_ROLES, required = false) String roles,
            @PathVariable UUID id) {

        if (roles == null || !roles.contains(SecurityConstants.ROLE_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only administrators can delete products"));
        }

        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deactivated successfully", null));
    }
}
