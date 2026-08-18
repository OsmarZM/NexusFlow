package com.nexusflow.messaging.event;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentProcessedEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String correlationId,
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        boolean success,
        String failureReason
) implements DomainEvent {

    public static PaymentProcessedEvent success(UUID paymentId, UUID orderId, BigDecimal amount, String correlationId) {
        return PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .eventType("PAYMENT_APPROVED")
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .paymentId(paymentId)
                .orderId(orderId)
                .amount(amount)
                .success(true)
                .build();
    }

    public static PaymentProcessedEvent failure(UUID orderId, BigDecimal amount, String failureReason, String correlationId) {
        return PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .eventType("PAYMENT_FAILED")
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .orderId(orderId)
                .amount(amount)
                .success(false)
                .failureReason(failureReason)
                .build();
    }

    @Override
    public UUID getEventId() { return eventId; }

    @Override
    public Instant getOccurredAt() { return occurredAt; }

    @Override
    public String getEventType() { return eventType; }

    @Override
    public String getCorrelationId() { return correlationId; }
}
