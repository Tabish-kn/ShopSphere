package com.shopsphere.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock payment gateway provider simulating Stripe/PayPal transactions.
 */
@Slf4j
@Component
public class MockPaymentGateway {

    public record GatewayResult(
            boolean successful,
            String transactionReference,
            String failureReason
    ) {}

    public GatewayResult processCharge(UUID orderId, BigDecimal amount, String paymentMethod) {
        log.info("Contacting external payment gateway for Order ID: {} | Amount: {}", orderId, amount);

        // Simulation rule: Amount >= 10,000 simulates an authorization decline
        if (amount.compareTo(new BigDecimal("10000.00")) >= 0) {
            log.warn("Payment declined for Order ID {}: Exceeded single transaction fraud limit", orderId);
            return new GatewayResult(false, null, "Exceeded transaction limit / Card authorization declined");
        }

        // Generate synthetic transaction reference
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomPart = ThreadLocalRandom.current().nextInt(100000, 999999);
        String txnRef = "TXN-" + datePart + "-" + randomPart;

        log.info("Payment captured successfully for Order ID: {}. Ref: {}", orderId, txnRef);
        return new GatewayResult(true, txnRef, null);
    }
}
