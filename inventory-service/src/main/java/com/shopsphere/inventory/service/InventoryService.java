package com.shopsphere.inventory.service;

import com.shopsphere.common.event.*;
import com.shopsphere.common.exception.ResourceNotFoundException;
import com.shopsphere.inventory.dto.InventoryResponse;
import com.shopsphere.inventory.entity.Inventory;
import com.shopsphere.inventory.entity.InventoryReservation;
import com.shopsphere.inventory.entity.ProcessedEvent;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.inventory.repository.InventoryReservationRepository;
import com.shopsphere.inventory.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByProductId(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        return mapToResponse(inventory);
    }

    @Transactional
    public InventoryResponse updateStock(UUID productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> Inventory.builder()
                        .productId(productId)
                        .availableQuantity(0)
                        .reservedQuantity(0)
                        .build());

        inventory.setAvailableQuantity(quantity);
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Updated stock for product {}: available={}", productId, quantity);
        return mapToResponse(saved);
    }

    /**
     * Saga Step 2: Reserves stock upon OrderCreatedEvent.
     * Uses Optimistic Locking (@Version) to prevent overselling.
     */
    @Transactional
    public void processOrderCreated(OrderCreatedEvent event) {
        String eventId = event.getEventId();

        // 1. Idempotency Check
        if (processedEventRepository.existsById(eventId)) {
            log.info("Event {} already processed. Skipping duplicate reservation.", eventId);
            return;
        }

        UUID orderId = event.getPayload().getOrderId();
        UUID userId = event.getPayload().getUserId();
        List<OrderCreatedEvent.OrderItemPayload> items = event.getPayload().getItems();

        log.info("Processing inventory reservation for Order ID: {}", orderId);

        // 2. Stock Availability Check
        for (OrderCreatedEvent.OrderItemPayload item : items) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId())
                    .orElse(null);

            if (inventory == null || inventory.getAvailableQuantity() < item.getQuantity()) {
                String failureReason = "Insufficient stock for product: " + item.getProductName() + " (SKU: " + item.getSku() + ")";
                log.warn("Inventory reservation failed for order {}: {}", orderId, failureReason);

                // Publish InventoryFailedEvent
                InventoryFailedEvent failedEvent = InventoryFailedEvent.builder()
                        .payload(InventoryFailedEvent.InventoryFailedPayload.builder()
                                .orderId(orderId)
                                .userId(userId)
                                .reason(failureReason)
                                .failedAt(Instant.now())
                                .build())
                        .build();
                failedEvent.initializeMetadata("INVENTORY_FAILED", "INVENTORY", orderId.toString(), event.getCorrelationId());

                kafkaTemplate.send("inventory-failed", orderId.toString(), failedEvent);
                recordProcessedEvent(eventId, "OrderCreatedHandler");
                return;
            }
        }

        // 3. Execute Stock Reservation with Optimistic Locking
        for (OrderCreatedEvent.OrderItemPayload item : items) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId()).orElseThrow();

            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - item.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);

            InventoryReservation reservation = InventoryReservation.builder()
                    .orderId(orderId)
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .status("RESERVED")
                    .build();
            reservationRepository.save(reservation);
        }

        // 4. Publish InventoryReservedEvent
        InventoryReservedEvent reservedEvent = InventoryReservedEvent.builder()
                .payload(InventoryReservedEvent.InventoryPayload.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .totalAmount(event.getPayload().getTotalAmount())
                        .currency(event.getPayload().getCurrency())
                        .paymentMethod(event.getPayload().getPaymentMethod())
                        .reservedAt(Instant.now())
                        .build())
                .build();
        reservedEvent.initializeMetadata("INVENTORY_RESERVED", "INVENTORY", orderId.toString(), event.getCorrelationId());

        kafkaTemplate.send("inventory-reserved", orderId.toString(), reservedEvent);
        log.info("Successfully reserved stock and emitted 'inventory-reserved' for order: {}", orderId);

        // 5. Emit real-time tracking notification event
        NotificationEvent notifEvent = NotificationEvent.builder()
                .payload(NotificationEvent.NotificationPayload.builder()
                        .userId(userId)
                        .title("Stock Reserved")
                        .message("Your items have been reserved. Processing payment.")
                        .notificationType("ORDER_STATUS")
                        .channel("WEBSOCKET")
                        .referenceId(orderId)
                        .build())
                .build();
        notifEvent.initializeMetadata("NOTIFICATION_EVENT", "NOTIFICATION", userId.toString(), event.getCorrelationId());
        kafkaTemplate.send("notification-events", userId.toString(), notifEvent);

        recordProcessedEvent(eventId, "OrderCreatedHandler");
    }

    /**
     * Finalizes stock deduction upon payment success.
     */
    @Transactional
    public void commitStock(UUID orderId) {
        log.info("Committing reserved stock for confirmed Order ID: {}", orderId);
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);

        for (InventoryReservation res : reservations) {
            if ("RESERVED".equals(res.getStatus())) {
                Inventory inventory = inventoryRepository.findByProductId(res.getProductId()).orElse(null);
                if (inventory != null) {
                    inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - res.getQuantity()));
                    inventoryRepository.save(inventory);
                }
                res.setStatus("COMMITTED");
                reservationRepository.save(res);
            }
        }
    }

    /**
     * Saga Compensating Transaction: Releases reserved stock upon payment failure.
     */
    @Transactional
    public void releaseReservedStock(UUID orderId) {
        log.warn("Executing Saga Compensation: Releasing reserved stock for Order ID: {}", orderId);
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);

        for (InventoryReservation res : reservations) {
            if ("RESERVED".equals(res.getStatus())) {
                Inventory inventory = inventoryRepository.findByProductId(res.getProductId()).orElse(null);
                if (inventory != null) {
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + res.getQuantity());
                    inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - res.getQuantity()));
                    inventoryRepository.save(inventory);
                }
                res.setStatus("RELEASED");
                reservationRepository.save(res);
            }
        }
    }

    private void recordProcessedEvent(String eventId, String handlerName) {
        try {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .handlerName(handlerName)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // Already recorded
        }
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .availableQuantity(inventory.getAvailableQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .version(inventory.getVersion())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
