package com.nexusflow.messaging.event;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String correlationId,
        UUID orderId,
        UUID customerId,
        BigDecimal amount
) implements DomainEvent {

    public static PaymentRequestedEvent create(UUID orderId, UUID customerId, BigDecimal amount, String correlationId) {
        return PaymentRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .eventType("PAYMENT_REQUESTED")
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
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
