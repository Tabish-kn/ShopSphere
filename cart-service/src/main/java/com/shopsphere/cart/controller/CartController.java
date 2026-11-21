package com.shopsphere.cart.controller;

import com.shopsphere.cart.dto.AddToCartRequest;
import com.shopsphere.cart.dto.CartResponse;
import com.shopsphere.cart.dto.UpdateQuantityRequest;
import com.shopsphere.cart.service.CartService;
import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.security.SecurityConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart operations (Redis backed)")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get current authenticated user's shopping cart")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId) {
        CartResponse response = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/items")
    @Operation(summary = "Add an item to the shopping cart")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse response = cartService.addItemToCart(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", response));
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Update quantity of a specific cart item")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateQuantityRequest request) {
        CartResponse response = cartService.updateItemQuantity(userId, productId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Cart item updated", response));
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove an item from the shopping cart")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @PathVariable UUID productId) {
        CartResponse response = cartService.removeItemFromCart(userId, productId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", response));
    }

    @DeleteMapping
    @Operation(summary = "Clear the entire shopping cart")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Shopping cart cleared", null));
    }
}
