package com.nexusflow.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class BusinessMetricsService {

    private final Counter ordersCreatedCounter;
    private final Counter ordersCancelledCounter;
    private final Counter paymentsApprovedCounter;
    private final Counter paymentsFailedCounter;
    private final Counter inventoryReservationsCounter;
    private final Counter rateLimitExceededCounter;
    private final Timer orderProcessingTimer;

    public BusinessMetricsService(MeterRegistry registry) {
        this.ordersCreatedCounter = Counter.builder("nexusflow.orders.created.total")
                .description("Total number of orders created")
                .register(registry);

        this.ordersCancelledCounter = Counter.builder("nexusflow.orders.cancelled.total")
                .description("Total number of orders cancelled")
                .register(registry);

        this.paymentsApprovedCounter = Counter.builder("nexusflow.payments.approved.total")
                .description("Total number of approved payments")
                .register(registry);

        this.paymentsFailedCounter = Counter.builder("nexusflow.payments.failed.total")
                .description("Total number of failed payments")
                .register(registry);

        this.inventoryReservationsCounter = Counter.builder("nexusflow.inventory.reservations.total")
                .description("Total number of stock reservations made")
                .register(registry);

        this.rateLimitExceededCounter = Counter.builder("nexusflow.ratelimit.exceeded.total")
                .description("Total number of rejected rate-limited requests")
                .register(registry);

        this.orderProcessingTimer = Timer.builder("nexusflow.orders.processing.duration")
                .description("Duration of order creation and stock reservation transactions")
                .register(registry);
    }

    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    public void incrementOrdersCancelled() {
        ordersCancelledCounter.increment();
    }

    public void incrementPaymentsApproved() {
        paymentsApprovedCounter.increment();
    }

    public void incrementPaymentsFailed() {
        paymentsFailedCounter.increment();
    }

    public void incrementInventoryReservations() {
        inventoryReservationsCounter.increment();
    }

    public void incrementRateLimitExceeded() {
        rateLimitExceededCounter.increment();
    }

    public void recordOrderProcessingDuration(long millis) {
        orderProcessingTimer.record(millis, TimeUnit.MILLISECONDS);
    }
}
