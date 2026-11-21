package com.shopsphere.order.kafka;

import com.shopsphere.common.event.*;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes downstream Saga events to transition the Order aggregate state.
 */
@Component
@RequiredArgsConstructor
public class SagaResponseConsumers {

    private static final Logger log = LoggerFactory.getLogger(SagaResponseConsumers.class);
    private final OrderService orderService;

    @KafkaListener(topics = "inventory-reserved", groupId = "order-group")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        UUID orderId = event.getPayload().getOrderId();
        log.info("Saga Update: Inventory reserved for order: {}. Moving to PAYMENT_PENDING.", orderId);
        orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING, null);
    }

    @KafkaListener(topics = "inventory-failed", groupId = "order-group")
    public void handleInventoryFailed(InventoryFailedEvent event) {
        UUID orderId = event.getPayload().getOrderId();
        log.warn("Saga Update: Inventory reservation failed for order: {}. Reason: {}. Transitioning to FAILED.",
                orderId, event.getPayload().getReason());
        orderService.updateOrderStatus(orderId, OrderStatus.FAILED, event.getPayload().getReason());
    }

    @KafkaListener(topics = "payment-success", groupId = "order-group")
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        UUID orderId = event.getPayload().getOrderId();
        log.info("Saga Update: Payment confirmed for order: {}. Transitioning to CONFIRMED.", orderId);
        orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, null);
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        UUID orderId = event.getPayload().getOrderId();
        log.warn("Saga Update: Payment failed for order: {}. Reason: {}. Transitioning to CANCELLED.",
                orderId, event.getPayload().getFailureReason());
        orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED, "Payment Declined: " + event.getPayload().getFailureReason());
    }
}
