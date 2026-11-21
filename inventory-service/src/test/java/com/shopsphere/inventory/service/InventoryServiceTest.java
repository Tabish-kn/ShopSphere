package com.shopsphere.inventory.service;

import com.shopsphere.common.event.OrderCreatedEvent;
import com.shopsphere.inventory.entity.Inventory;
import com.shopsphere.inventory.repository.InventoryRepository;
import com.shopsphere.inventory.repository.InventoryReservationRepository;
import com.shopsphere.inventory.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID productId;
    private UUID orderId;
    private UUID userId;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();

        inventory = Inventory.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .availableQuantity(10)
                .reservedQuantity(0)
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("Should successfully reserve stock and emit inventory-reserved event")
    void shouldReserveStockSuccessfully() {
        OrderCreatedEvent.OrderItemPayload item = OrderCreatedEvent.OrderItemPayload.builder()
                .productId(productId)
                .sku("SKU-TEST")
                .productName("Test Product")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId("corr-123")
                .payload(OrderCreatedEvent.OrderPayload.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .totalAmount(new BigDecimal("100.00"))
                        .currency("USD")
                        .paymentMethod("CREDIT_CARD")
                        .items(List.of(item))
                        .build())
                .build();

        when(processedEventRepository.existsById(anyString())).thenReturn(false);
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        inventoryService.processOrderCreated(event);

        // Verify stock decremented
        assertEquals(8, inventory.getAvailableQuantity());
        assertEquals(2, inventory.getReservedQuantity());
        verify(inventoryRepository, times(1)).save(inventory);
        verify(reservationRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(eq("inventory-reserved"), eq(orderId.toString()), any());
    }

    @Test
    @DisplayName("Should fail reservation and emit inventory-failed when stock is insufficient")
    void shouldFailReservationWhenOutOfStock() {
        OrderCreatedEvent.OrderItemPayload item = OrderCreatedEvent.OrderItemPayload.builder()
                .productId(productId)
                .sku("SKU-TEST")
                .productName("Test Product")
                .quantity(50) // More than available (10)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId("corr-123")
                .payload(OrderCreatedEvent.OrderPayload.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .items(List.of(item))
                        .build())
                .build();

        when(processedEventRepository.existsById(anyString())).thenReturn(false);
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        inventoryService.processOrderCreated(event);

        // Quantities unchanged
        assertEquals(10, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        verify(kafkaTemplate, times(1)).send(eq("inventory-failed"), eq(orderId.toString()), any());
        verify(kafkaTemplate, never()).send(eq("inventory-reserved"), anyString(), any());
    }
}
