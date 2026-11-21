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
 * Emitted by Inventory Service when stock is insufficient.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InventoryFailedEvent extends BaseEvent<InventoryFailedEvent.InventoryFailedPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryFailedPayload implements Serializable {
        private UUID orderId;
        private UUID userId;
        private String reason;
        private Instant failedAt;
    }
}
