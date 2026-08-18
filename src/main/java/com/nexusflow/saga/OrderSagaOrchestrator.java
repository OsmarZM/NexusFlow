package com.nexusflow.saga;

import com.nexusflow.idempotency.application.IdempotencyService;
import com.nexusflow.messaging.event.PaymentProcessedEvent;
import com.nexusflow.order.application.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private static final String CONSUMER_NAME = "OrderSagaOrchestrator";

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(
            topics = "${nexusflow.kafka.topics.payment-processed:payments.processed}",
            groupId = "nexusflow-saga-orchestrator"
    )
    public void handlePaymentProcessed(
            @Payload PaymentProcessedEvent event,
            @Header(name = "correlationId", required = false) String correlationId) {

        log.info("Saga Orchestrator received PaymentProcessedEvent for order: {}, success: {}, correlationId: {}",
                event.orderId(), event.success(), correlationId);

        // 1. Idempotency check
        if (idempotencyService.isAlreadyProcessed(event.getEventId(), CONSUMER_NAME)) {
            log.warn("Duplicate PaymentProcessedEvent detected [{}] by {}. Skipping.", event.getEventId(), CONSUMER_NAME);
            return;
        }

        try {
            if (event.success()) {
                // Happy path: Confirm order & deduct physical stock
                log.info("Payment approved for Order {}. Confirming order.", event.orderId());
                orderService.markAsPaid(event.orderId());
            } else {
                // Compensating Transaction: Payment failed -> release inventory & cancel order
                log.warn("Payment failed for Order {}. Executing Saga Compensating Transaction: Releasing stock reservations.",
                        event.orderId());
                orderService.cancelOrder(event.orderId(), "SAGA_COMPENSATION: " + event.failureReason());
            }

            // 2. Mark event as processed idempotently
            idempotencyService.markAsProcessed(event.getEventId(), event.getEventType(), CONSUMER_NAME);
            log.info("Saga step for order {} completed successfully.", event.orderId());

        } catch (Exception e) {
            log.error("Error executing Saga step for order {}: {}", event.orderId(), e.getMessage(), e);
            throw e;
        }
    }
}
