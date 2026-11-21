package com.shopsphere.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by Inventory Service after stock has been successfully reserved.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InventoryReservedEvent extends BaseEvent<InventoryReservedEvent.InventoryPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryPayload implements Serializable {
        private UUID orderId;
        private UUID userId;
        private BigDecimal totalAmount;
        private String currency;
        private String paymentMethod;
        private Instant reservedAt;
    }
}
