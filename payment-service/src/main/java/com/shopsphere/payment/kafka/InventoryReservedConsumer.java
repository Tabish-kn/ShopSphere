package com.shopsphere.payment.kafka;

import com.shopsphere.common.event.InventoryReservedEvent;
import com.shopsphere.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryReservedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservedConsumer.class);
    private final PaymentService paymentService;

    @KafkaListener(topics = "inventory-reserved", groupId = "payment-group")
    public void consume(InventoryReservedEvent event) {
        log.info("Received InventoryReservedEvent for order: {}. Proceeding with payment authorization.",
                event.getPayload().getOrderId());
        paymentService.processInventoryReserved(event);
    }
}
