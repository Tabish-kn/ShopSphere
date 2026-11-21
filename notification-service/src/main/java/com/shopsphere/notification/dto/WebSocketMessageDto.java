package com.shopsphere.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessageDto {

    private String type; // ORDER_STATUS_CHANGE, PAYMENT_STATUS, PRIVATE_ALERT
    private String title;
    private String message;
    private UUID referenceId; // orderId
    private String status;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
