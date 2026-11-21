package com.shopsphere.cart.service;

import com.shopsphere.cart.client.ProductFeignClient;
import com.shopsphere.cart.dto.AddToCartRequest;
import com.shopsphere.cart.dto.CartResponse;
import com.shopsphere.cart.model.Cart;
import com.shopsphere.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ProductFeignClient productFeignClient;

    @InjectMocks
    private CartService cartService;

    private UUID userId;
    private UUID productId;
    private ProductFeignClient.ProductSnapshotDto productSnapshot;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cartService, "cartTtlHours", 72L);

        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        productSnapshot = new ProductFeignClient.ProductSnapshotDto(
                productId,
                "Ergonomic Gaming Mouse",
                "MOUSE-RGB-001",
                new BigDecimal("49.99"),
                true,
                "https://assets.shopsphere.com/mouse.jpg"
        );
    }

    @Test
    @DisplayName("Should successfully add a new item to cart and calculate subtotals")
    void shouldAddItemToCart() {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(productId)
                .quantity(2)
                .build();

        when(productFeignClient.getProductById(productId))
                .thenReturn(ApiResponse.success(productSnapshot));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cart:" + userId)).thenReturn(null);

        CartResponse response = cartService.addItemToCart(userId, request);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(1, response.getItems().size());
        assertEquals(2, response.getTotalItemsCount());
        assertEquals(new BigDecimal("99.98"), response.getTotalAmount());
        verify(valueOperations, times(1)).set(eq("cart:" + userId), any(Cart.class), any(Duration.class));
    }

    @Test
    @DisplayName("Should clear shopping cart from Redis")
    void shouldClearCart() {
        when(redisTemplate.delete("cart:" + userId)).thenReturn(true);

        cartService.clearCart(userId);

        verify(redisTemplate, times(1)).delete("cart:" + userId);
    }
}
