package com.shopsphere.payment.service;

import com.shopsphere.common.event.*;
import com.shopsphere.common.exception.ConflictException;
import com.shopsphere.common.exception.ResourceNotFoundException;
import com.shopsphere.payment.dto.PaymentRequest;
import com.shopsphere.payment.dto.PaymentResponse;
import com.shopsphere.payment.entity.Payment;
import com.shopsphere.payment.entity.PaymentStatus;
import com.shopsphere.payment.entity.ProcessedEvent;
import com.shopsphere.payment.gateway.MockPaymentGateway;
import com.shopsphere.payment.repository.PaymentRepository;
import com.shopsphere.payment.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final MockPaymentGateway mockPaymentGateway;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));
        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        return mapToResponse(payment);
    }

    /**
     * Direct API payment processing with explicit Idempotency Key.
     */
    @Transactional
    public PaymentResponse processDirectPayment(UUID userId, String idempotencyKey, PaymentRequest request) {
        if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
            Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
            log.info("Idempotent hit: Returning existing payment record for key: {}", idempotencyKey);
            return mapToResponse(existing);
        }

        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            throw new ConflictException("Payment for order " + request.getOrderId() + " has already been processed");
        }

        MockPaymentGateway.GatewayResult result = mockPaymentGateway.processCharge(
                request.getOrderId(), request.getAmount(), request.getPaymentMethod());

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(userId)
                .amount(request.getAmount())
                .currency("USD")
                .status(result.successful() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .transactionReference(result.successful() ? result.transactionReference() : "FAILED-" + UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .paymentMethod(request.getPaymentMethod())
                .failureReason(result.failureReason())
                .build();

        Payment saved = paymentRepository.save(payment);
        return mapToResponse(saved);
    }

    /**
     * Saga Step 3: Triggered asynchronously by InventoryReservedEvent.
     */
    @Transactional
    public void processInventoryReserved(InventoryReservedEvent event) {
        String eventId = event.getEventId();

        // 1. Idempotency Check
        if (processedEventRepository.existsById(eventId)) {
            log.info("InventoryReservedEvent {} already processed. Skipping duplicate payment.", eventId);
            return;
        }

        UUID orderId = event.getPayload().getOrderId();
        UUID userId = event.getPayload().getUserId();
        String correlationId = event.getCorrelationId();

        log.info("Executing Saga Step 3: Processing payment for Order ID: {}", orderId);

        String idempotencyKey = "saga-order-" + orderId;
        if (paymentRepository.existsByOrderId(orderId)) {
            log.warn("Payment for order {} already exists in DB. Skipping charge.", orderId);
            recordProcessedEvent(eventId, "InventoryReservedHandler");
            return;
        }

        // 2. Charge payment via gateway
        MockPaymentGateway.GatewayResult result = mockPaymentGateway.processCharge(
                orderId, event.getPayload().getTotalAmount(), event.getPayload().getPaymentMethod());

        if (result.successful()) {
            // Happy Path
            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .amount(event.getPayload().getTotalAmount())
                    .currency(event.getPayload().getCurrency())
                    .status(PaymentStatus.SUCCESS)
                    .transactionReference(result.transactionReference())
                    .idempotencyKey(idempotencyKey)
                    .paymentMethod(event.getPayload().getPaymentMethod())
                    .build();

            Payment saved = paymentRepository.save(payment);

            // Publish PaymentSuccessEvent
            PaymentSuccessEvent successEvent = PaymentSuccessEvent.builder()
                    .payload(PaymentSuccessEvent.PaymentSuccessPayload.builder()
                            .paymentId(saved.getId())
                            .orderId(orderId)
                            .userId(userId)
                            .amount(saved.getAmount())
                            .currency(saved.getCurrency())
                            .transactionReference(saved.getTransactionReference())
                            .processedAt(Instant.now())
                            .build())
                    .build();
            successEvent.initializeMetadata("PAYMENT_SUCCESS", "PAYMENT", orderId.toString(), correlationId);

            kafkaTemplate.send("payment-success", orderId.toString(), successEvent);
            log.info("Emitted 'payment-success' for Order: {}", orderId);

            // Emit Real-time Notification
            NotificationEvent notif = NotificationEvent.builder()
                    .payload(NotificationEvent.NotificationPayload.builder()
                            .userId(userId)
                            .title("Payment Confirmed")
                            .message("Payment of $" + saved.getAmount() + " successfully captured.")
                            .notificationType("PAYMENT_ALERT")
                            .channel("BOTH")
                            .referenceId(orderId)
                            .build())
                    .build();
            notif.initializeMetadata("NOTIFICATION_EVENT", "NOTIFICATION", userId.toString(), correlationId);
            kafkaTemplate.send("notification-events", userId.toString(), notif);

        } else {
            // Failure Path: Trigger Compensating Transaction in Order & Inventory Services
            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .amount(event.getPayload().getTotalAmount())
                    .currency(event.getPayload().getCurrency())
                    .status(PaymentStatus.FAILED)
                    .transactionReference("FAILED-" + UUID.randomUUID())
                    .idempotencyKey(idempotencyKey)
                    .paymentMethod(event.getPayload().getPaymentMethod())
                    .failureReason(result.failureReason())
                    .build();

            paymentRepository.save(payment);

            PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                    .payload(PaymentFailedEvent.PaymentFailedPayload.builder()
                            .orderId(orderId)
                            .userId(userId)
                            .amount(event.getPayload().getTotalAmount())
                            .currency(event.getPayload().getCurrency())
                            .failureReason(result.failureReason())
                            .failedAt(Instant.now())
                            .build())
                    .build();
            failedEvent.initializeMetadata("PAYMENT_FAILED", "PAYMENT", orderId.toString(), correlationId);

            kafkaTemplate.send("payment-failed", orderId.toString(), failedEvent);
            log.warn("Emitted 'payment-failed' for Order: {}. Triggering Saga compensations.", orderId);
        }

        recordProcessedEvent(eventId, "InventoryReservedHandler");
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

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .transactionReference(payment.getTransactionReference())
                .paymentMethod(payment.getPaymentMethod())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
