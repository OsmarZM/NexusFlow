package com.nexusflow.messaging.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record OrderCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String correlationId,
        UUID orderId,
        String reason
) implements DomainEvent {

    public static OrderCancelledEvent create(UUID orderId, String reason, String correlationId) {
        return OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .eventType("ORDER_CANCELLED")
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .orderId(orderId)
                .reason(reason)
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
