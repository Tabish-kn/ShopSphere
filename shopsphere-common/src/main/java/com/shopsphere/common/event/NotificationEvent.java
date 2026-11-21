package com.shopsphere.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.UUID;

/**
 * Emitted to trigger multi-channel alerts (Email, WebSocket push).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationEvent extends BaseEvent<NotificationEvent.NotificationPayload> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPayload implements Serializable {
        private UUID userId;
        private String recipientEmail;
        private String title;
        private String message;
        private String notificationType; // ORDER_STATUS, PAYMENT_ALERT, INVENTORY_ALERT
        private String channel;          // EMAIL, WEBSOCKET, BOTH
        private UUID referenceId;        // e.g. orderId
    }
}
