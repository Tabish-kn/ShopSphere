package com.shopsphere.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.order.client.CartFeignClient;
import com.shopsphere.order.dto.CreateOrderRequest;
import com.shopsphere.order.dto.OrderResponse;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderOutbox;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.repository.OrderOutboxRepository;
import com.shopsphere.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Mock
    private CartFeignClient cartFeignClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should successfully create order and record outbox event in same transaction")
    void shouldCreateOrderAndOutboxEvent() throws Exception {
        CreateOrderRequest.OrderItemRequest itemReq = CreateOrderRequest.OrderItemRequest.builder()
                .productId(productId)
                .productName("Mechanical Keyboard")
                .sku("KEYBOARD-RGB")
                .unitPrice(new BigDecimal("99.99"))
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress("123 Market St, San Francisco, CA")
                .paymentMethod("CREDIT_CARD")
                .items(List.of(itemReq))
                .build();

        Order savedOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("ORD-20261121-1234")
                .userId(userId)
                .totalAmount(new BigDecimal("99.99"))
                .status(OrderStatus.CREATED)
                .shippingAddressJson(request.getShippingAddress())
                .paymentMethod("CREDIT_CARD")
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"test\"}");

        OrderResponse response = orderService.createOrder(userId, request, "corr-test-1");

        assertNotNull(response);
        assertEquals("ORD-20261121-1234", response.getOrderNumber());
        assertEquals(OrderStatus.CREATED, response.getStatus());

        // Verify atomic outbox insertion
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(outboxRepository, times(1)).save(any(OrderOutbox.class));
        verify(cartFeignClient, times(1)).clearCart(userId);
    }
}
