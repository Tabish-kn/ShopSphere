package com.shopsphere.payment.controller;

import com.shopsphere.common.dto.ApiResponse;
import com.shopsphere.common.security.SecurityConstants;
import com.shopsphere.payment.dto.PaymentRequest;
import com.shopsphere.payment.dto.PaymentResponse;
import com.shopsphere.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment Processing and Transaction Auditing APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    @Operation(summary = "Process a direct payment transaction with Idempotency Key")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response = paymentService.processDirectPayment(userId, idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success("Payment processed", response));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment transaction details by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(@PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment details for a specific order")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrderId(@PathVariable UUID orderId) {
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
