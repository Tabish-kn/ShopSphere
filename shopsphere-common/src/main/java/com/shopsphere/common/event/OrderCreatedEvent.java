package com.shopsphere.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Emitted by Order Service to initiate the order fulfillment Saga.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderCreatedEvent extends BaseEvent<OrderCreatedEvent.OrderPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPayload implements Serializable {
        private UUID orderId;
        private String orderNumber;
        private UUID userId;
        private BigDecimal totalAmount;
        private String currency;
        private String paymentMethod;
        private List<OrderItemPayload> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemPayload implements Serializable {
        private UUID productId;
        private String sku;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
