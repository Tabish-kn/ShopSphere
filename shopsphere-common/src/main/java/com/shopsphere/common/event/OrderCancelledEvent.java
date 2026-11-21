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
 * Emitted by Order Service when order is CANCELLED (e.g. payment failed compensation).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderCancelledEvent extends BaseEvent<OrderCancelledEvent.OrderCancelledPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCancelledPayload implements Serializable {
        private UUID orderId;
        private UUID userId;
        private String cancellationReason;
        private Instant cancelledAt;
    }
}
