package com.shopsphere.order.dto;

import com.shopsphere.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private String orderNumber;
    private UUID userId;
    private BigDecimal totalAmount;
    private String currency;
    private OrderStatus status;
    private String shippingAddress;
    private String paymentMethod;
    private String cancellationReason;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}
