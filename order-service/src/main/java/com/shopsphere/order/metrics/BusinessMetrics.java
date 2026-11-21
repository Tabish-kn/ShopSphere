package com.shopsphere.order.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom domain metric meters for Prometheus scraping in Order Service.
 */
@Component
public class BusinessMetrics {

    private final Counter ordersPlacedCounter;
    private final Counter ordersFailedCounter;
    private final Counter paymentsSuccessCounter;
    private final Counter paymentsFailedCounter;
    private final Counter stockReservationsCounter;

    public BusinessMetrics(MeterRegistry registry) {
        this.ordersPlacedCounter = Counter.builder("shopsphere.orders.placed.total")
                .description("Total number of customer orders initiated")
                .register(registry);

        this.ordersFailedCounter = Counter.builder("shopsphere.orders.failed.total")
                .description("Total number of orders failed in Saga")
                .register(registry);

        this.paymentsSuccessCounter = Counter.builder("shopsphere.payments.success.total")
                .description("Total number of successful payment authorizations")
                .register(registry);

        this.paymentsFailedCounter = Counter.builder("shopsphere.payments.failed.total")
                .description("Total number of failed payment authorizations")
                .register(registry);

        this.stockReservationsCounter = Counter.builder("shopsphere.stock.reservations.total")
                .description("Total number of successful stock allocations")
                .register(registry);
    }

    public void incrementOrdersPlaced() {
        ordersPlacedCounter.increment();
    }

    public void incrementOrdersFailed() {
        ordersFailedCounter.increment();
    }

    public void incrementPaymentsSuccess() {
        paymentsSuccessCounter.increment();
    }

    public void incrementPaymentsFailed() {
        paymentsFailedCounter.increment();
    }

    public void incrementStockReservations() {
        stockReservationsCounter.increment();
    }
}
