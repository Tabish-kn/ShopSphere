package com.shopsphere.order.e2e;

import com.shopsphere.common.event.PaymentSuccessEvent;
import com.shopsphere.order.client.CartFeignClient;
import com.shopsphere.order.dto.CreateOrderRequest;
import com.shopsphere.order.dto.OrderResponse;
import com.shopsphere.order.entity.Order;
import com.shopsphere.order.entity.OrderOutbox;
import com.shopsphere.order.entity.OrderStatus;
import com.shopsphere.order.kafka.SagaResponseConsumers;
import com.shopsphere.order.outbox.OutboxPublisherService;
import com.shopsphere.order.repository.OrderOutboxRepository;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OrderWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Autowired
    private SagaResponseConsumers sagaResponseConsumers;

    @MockBean
    private CartFeignClient cartFeignClient;

    @Test
    @DisplayName("Complete E2E Order-to-Payment Saga Workflow with Testcontainers")
    void shouldExecuteEndToEndOrderSagaWorkflow() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CreateOrderRequest.OrderItemRequest item = CreateOrderRequest.OrderItemRequest.builder()
                .productId(productId)
                .productName("Ergonomic Keyboard")
                .sku("KB-ERG-01")
                .unitPrice(new BigDecimal("129.99"))
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress("456 Market St, Seattle, WA")
                .paymentMethod("CREDIT_CARD")
                .items(List.of(item))
                .build();

        // 1. Initiate Order Placement (Atomic Order + Outbox insertion)
        OrderResponse createdOrder = orderService.createOrder(userId, request, "corr-e2e-test-1");

        assertNotNull(createdOrder.getId());
        assertEquals(OrderStatus.CREATED, createdOrder.getStatus());
        assertEquals(new BigDecimal("129.99"), createdOrder.getTotalAmount());

        // 2. Verify Database State
        Order orderInDb = orderRepository.findById(createdOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.CREATED, orderInDb.getStatus());

        List<OrderOutbox> outboxList = outboxRepository.findPendingEvents();
        assertFalse(outboxList.isEmpty());
        OrderOutbox outbox = outboxList.stream()
                .filter(o -> o.getAggregateId().equals(createdOrder.getId().toString()))
                .findFirst()
                .orElseThrow();
        assertEquals("PENDING", outbox.getStatus());
        assertEquals("ORDER_CREATED", outbox.getEventType());

        // 3. Trigger Outbox Publisher Worker to emit to Testcontainers Kafka
        outboxPublisherService.publishPendingEvents();

        OrderOutbox publishedOutbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals("PUBLISHED", publishedOutbox.getStatus());
        assertNotNull(publishedOutbox.getPublishedAt());

        // 4. Simulate Downstream Saga Event: Payment Success Callback
        PaymentSuccessEvent paymentSuccessEvent = PaymentSuccessEvent.builder()
                .payload(PaymentSuccessEvent.PaymentSuccessPayload.builder()
                        .orderId(createdOrder.getId())
                        .userId(userId)
                        .paymentId(UUID.randomUUID())
                        .amount(new BigDecimal("129.99"))
                        .currency("USD")
                        .transactionReference("TXN-E2E-999")
                        .processedAt(Instant.now())
                        .build())
                .build();
        paymentSuccessEvent.initializeMetadata("PAYMENT_SUCCESS", "PAYMENT", createdOrder.getId().toString(), "corr-e2e-test-1");

        sagaResponseConsumers.handlePaymentSuccess(paymentSuccessEvent);

        // 5. Verify Order status transitioned to CONFIRMED
        Order confirmedOrder = orderRepository.findById(createdOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
        assertNull(confirmedOrder.getCancellationReason());
    }
}
