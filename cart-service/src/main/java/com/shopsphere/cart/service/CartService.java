package com.shopsphere.cart.service;

import com.shopsphere.cart.client.ProductFeignClient;
import com.shopsphere.cart.dto.AddToCartRequest;
import com.shopsphere.cart.dto.CartItemResponse;
import com.shopsphere.cart.dto.CartResponse;
import com.shopsphere.cart.model.Cart;
import com.shopsphere.cart.model.CartItem;
import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.exception.BadRequestException;
import com.shopsphere.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final String CART_KEY_PREFIX = "cart:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductFeignClient productFeignClient;

    @Value("${cart.ttl-hours:72}")
    private long cartTtlHours;

    public CartResponse getCart(UUID userId) {
        String key = getCartKey(userId);
        Cart cart = (Cart) redisTemplate.opsForValue().get(key);

        if (cart == null) {
            cart = Cart.builder()
                    .userId(userId)
                    .items(new HashMap<>())
                    .totalAmount(BigDecimal.ZERO)
                    .updatedAt(Instant.now())
                    .build();
        }

        return mapToResponse(cart);
    }

    public CartResponse addItemToCart(UUID userId, AddToCartRequest request) {
        UUID productId = request.getProductId();

        // Synchronously fetch verified product details via OpenFeign + Resilience4j
        ApiResponse<ProductFeignClient.ProductSnapshotDto> productResponse = productFeignClient.getProductById(productId);
        if (productResponse == null || !productResponse.isSuccess() || productResponse.getData() == null) {
            throw new BadRequestException("Product is unavailable or does not exist: " + productId);
        }

        ProductFeignClient.ProductSnapshotDto product = productResponse.getData();
        if (!product.active()) {
            throw new BadRequestException("Product '" + product.name() + "' is currently inactive.");
        }

        String key = getCartKey(userId);
        Cart cart = (Cart) redisTemplate.opsForValue().get(key);

        if (cart == null) {
            cart = Cart.builder()
                    .userId(userId)
                    .items(new HashMap<>())
                    .build();
        }

        if (cart.getItems().containsKey(productId)) {
            // Increment existing item quantity
            CartItem existingItem = cart.getItems().get(productId);
            existingItem.updateQuantity(existingItem.getQuantity() + request.getQuantity());
        } else {
            // Add new cart item
            BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(request.getQuantity()));
            CartItem newItem = CartItem.builder()
                    .productId(productId)
                    .sku(product.sku())
                    .productName(product.name())
                    .unitPrice(product.price())
                    .quantity(request.getQuantity())
                    .subtotal(subtotal)
                    .imageUrl(product.imageUrl())
                    .build();
            cart.getItems().put(productId, newItem);
        }

        cart.recalculateTotals();
        saveCartWithTtl(key, cart);

        log.info("Added product {} (qty: {}) to cart for user {}", productId, request.getQuantity(), userId);
        return mapToResponse(cart);
    }

    public CartResponse updateItemQuantity(UUID userId, UUID productId, int quantity) {
        String key = getCartKey(userId);
        Cart cart = (Cart) redisTemplate.opsForValue().get(key);

        if (cart == null || !cart.getItems().containsKey(productId)) {
            throw new ResourceNotFoundException("CartItem", "productId", productId);
        }

        CartItem item = cart.getItems().get(productId);
        item.updateQuantity(quantity);
        cart.recalculateTotals();

        saveCartWithTtl(key, cart);
        log.info("Updated quantity for product {} to {} in cart for user {}", productId, quantity, userId);
        return mapToResponse(cart);
    }

    public CartResponse removeItemFromCart(UUID userId, UUID productId) {
        String key = getCartKey(userId);
        Cart cart = (Cart) redisTemplate.opsForValue().get(key);

        if (cart != null && cart.getItems().containsKey(productId)) {
            cart.getItems().remove(productId);
            cart.recalculateTotals();
            saveCartWithTtl(key, cart);
            log.info("Removed product {} from cart for user {}", productId, userId);
        }

        return cart != null ? mapToResponse(cart) : getCart(userId);
    }

    public void clearCart(UUID userId) {
        String key = getCartKey(userId);
        redisTemplate.delete(key);
        log.info("Cleared shopping cart for user {}", userId);
    }

    private void saveCartWithTtl(String key, Cart cart) {
        redisTemplate.opsForValue().set(key, cart, Duration.ofHours(cartTtlHours));
    }

    private String getCartKey(UUID userId) {
        return CART_KEY_PREFIX + userId.toString();
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().values().stream()
                .map(item -> CartItemResponse.builder()
                        .productId(item.getProductId())
                        .sku(item.getSku())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getSubtotal())
                        .imageUrl(item.getImageUrl())
                        .build())
                .toList();

        int totalCount = items.stream().mapToInt(CartItemResponse::getQuantity).sum();

        return CartResponse.builder()
                .userId(cart.getUserId())
                .items(items)
                .totalAmount(cart.getTotalAmount())
                .totalItemsCount(totalCount)
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
