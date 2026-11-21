package com.shopsphere.payment.service;

import com.shopsphere.common.event.InventoryReservedEvent;
import com.shopsphere.payment.entity.Payment;
import com.shopsphere.payment.entity.PaymentStatus;
import com.shopsphere.payment.gateway.MockPaymentGateway;
import com.shopsphere.payment.repository.PaymentRepository;
import com.shopsphere.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private MockPaymentGateway mockPaymentGateway;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PaymentService paymentService;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should successfully capture payment and emit payment-success event")
    void shouldCapturePaymentSuccessfully() {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId("corr-pay-1")
                .payload(InventoryReservedEvent.InventoryPayload.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .totalAmount(new BigDecimal("199.99"))
                        .currency("USD")
                        .paymentMethod("CREDIT_CARD")
                        .reservedAt(Instant.now())
                        .build())
                .build();

        when(processedEventRepository.existsById(anyString())).thenReturn(false);
        when(paymentRepository.existsByOrderId(orderId)).thenReturn(false);
        when(mockPaymentGateway.processCharge(eq(orderId), any(BigDecimal.class), anyString()))
                .thenReturn(new MockPaymentGateway.GatewayResult(true, "TXN-20261121-123456", null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        paymentService.processInventoryReserved(event);

        verify(paymentRepository, times(1)).save(argThat(p -> p.getStatus() == PaymentStatus.SUCCESS));
        verify(kafkaTemplate, times(1)).send(eq("payment-success"), eq(orderId.toString()), any());
        verify(kafkaTemplate, times(1)).send(eq("notification-events"), eq(userId.toString()), any());
    }

    @Test
    @DisplayName("Should emit payment-failed event to trigger Saga compensation when gateway declines")
    void shouldEmitPaymentFailedWhenGatewayDeclines() {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId("corr-pay-2")
                .payload(InventoryReservedEvent.InventoryPayload.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .totalAmount(new BigDecimal("15000.00")) // Exceeds limit
                        .currency("USD")
                        .paymentMethod("CREDIT_CARD")
                        .reservedAt(Instant.now())
                        .build())
                .build();

        when(processedEventRepository.existsById(anyString())).thenReturn(false);
        when(paymentRepository.existsByOrderId(orderId)).thenReturn(false);
        when(mockPaymentGateway.processCharge(eq(orderId), any(BigDecimal.class), anyString()))
                .thenReturn(new MockPaymentGateway.GatewayResult(false, null, "Card declined"));

        paymentService.processInventoryReserved(event);

        verify(paymentRepository, times(1)).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
        verify(kafkaTemplate, times(1)).send(eq("payment-failed"), eq(orderId.toString()), any());
        verify(kafkaTemplate, never()).send(eq("payment-success"), anyString(), any());
    }
}
