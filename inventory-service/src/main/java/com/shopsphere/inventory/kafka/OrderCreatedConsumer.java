package com.shopsphere.inventory.kafka;

import com.shopsphere.common.event.OrderCreatedEvent;
import com.shopsphere.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);
    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    public void consume(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order: {}", event.getPayload().getOrderId());
        inventoryService.processOrderCreated(event);
    }
}
