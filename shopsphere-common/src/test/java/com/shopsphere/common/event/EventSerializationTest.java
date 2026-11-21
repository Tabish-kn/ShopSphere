package com.shopsphere.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should correctly serialize and deserialize OrderCreatedEvent with metadata")
    void shouldSerializeAndDeserializeOrderCreatedEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        OrderCreatedEvent.OrderItemPayload item = OrderCreatedEvent.OrderItemPayload.builder()
                .productId(productId)
                .sku("SKU-1001")
                .productName("Mechanical Keyboard")
                .quantity(2)
                .unitPrice(new BigDecimal("99.99"))
                .build();

        OrderCreatedEvent.OrderPayload payload = OrderCreatedEvent.OrderPayload.builder()
                .orderId(orderId)
                .orderNumber("ORD-20261121-0001")
                .userId(userId)
                .totalAmount(new BigDecimal("199.98"))
                .currency("USD")
                .paymentMethod("CREDIT_CARD")
                .items(List.of(item))
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ORDER_CREATED")
                .eventTimestamp(Instant.now())
                .aggregateType("ORDER")
                .aggregateId(orderId.toString())
                .correlationId("corr-test-1234")
                .version(1)
                .payload(payload)
                .build();

        // When
        String json = objectMapper.writeValueAsString(event);
        assertNotNull(json);

        OrderCreatedEvent deserialized = objectMapper.readValue(json, OrderCreatedEvent.class);

        // Then
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals("ORDER_CREATED", deserialized.getEventType());
        assertEquals(event.getCorrelationId(), deserialized.getCorrelationId());
        assertEquals(orderId, deserialized.getPayload().getOrderId());
        assertEquals(1, deserialized.getPayload().getItems().size());
        assertEquals("SKU-1001", deserialized.getPayload().getItems().get(0).getSku());
    }
}
