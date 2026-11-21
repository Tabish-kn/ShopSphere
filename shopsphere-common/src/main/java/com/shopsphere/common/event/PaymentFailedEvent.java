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
 * Emitted by Payment Service when payment fails, triggering Saga compensation.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentFailedEvent extends BaseEvent<PaymentFailedEvent.PaymentFailedPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentFailedPayload implements Serializable {
        private UUID orderId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String failureReason;
        private Instant failedAt;
    }
}
