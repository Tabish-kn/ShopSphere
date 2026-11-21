package com.shopsphere.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by Order Service when order reaches CONFIRMED status.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderConfirmedEvent extends BaseEvent<OrderConfirmedEvent.OrderConfirmedPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderConfirmedPayload implements Serializable {
        private UUID orderId;
        private String orderNumber;
        private UUID userId;
        private Instant confirmedAt;
    }
}
