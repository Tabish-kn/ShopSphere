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
 * Emitted by Payment Service after payment is successfully captured.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentSuccessEvent extends BaseEvent<PaymentSuccessEvent.PaymentSuccessPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSuccessPayload implements Serializable {
        private UUID paymentId;
        private UUID orderId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String transactionReference;
        private Instant processedAt;
    }
}
