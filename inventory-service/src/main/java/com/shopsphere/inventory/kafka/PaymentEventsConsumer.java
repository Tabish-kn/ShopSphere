package com.shopsphere.inventory.kafka;

import com.shopsphere.common.event.PaymentFailedEvent;
import com.shopsphere.common.event.PaymentSuccessEvent;
import com.shopsphere.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsConsumer.class);
    private final InventoryService inventoryService;

    @KafkaListener(topics = "payment-success", groupId = "inventory-group")
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        UUID orderId = event.getPayload().getOrderId();
        log.info("Payment confirmed for order: {}. Finalizing stock deduction.", orderId);
        inventoryService.commitStock(orderId);
    }

    @KafkaListener(topics = "payment-failed", groupId = "inventory-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        UUID orderId = event.getPayload().getOrderId();
        log.warn("Payment failed for order: {}. Triggering compensating stock release.", orderId);
        inventoryService.releaseReservedStock(orderId);
    }
}
