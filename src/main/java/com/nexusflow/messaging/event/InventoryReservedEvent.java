package com.nexusflow.messaging.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record InventoryReservedEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String correlationId,
        UUID reservationId,
        UUID orderId,
        String sku,
        int quantity
) implements DomainEvent {

    public static InventoryReservedEvent create(UUID reservationId, UUID orderId, String sku, int quantity, String correlationId) {
        return InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .eventType("INVENTORY_RESERVED")
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .reservationId(reservationId)
                .orderId(orderId)
                .sku(sku)
                .quantity(quantity)
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
