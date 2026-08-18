package com.nexusflow.messaging.event;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String correlationId,
        UUID orderId,
        UUID customerId,
        String customerEmail,
        BigDecimal totalAmount,
        List<OrderItemPayload> items
) implements DomainEvent {

    public static OrderCreatedEvent create(
            UUID orderId,
            UUID customerId,
            String customerEmail,
            BigDecimal totalAmount,
            List<OrderItemPayload> items,
            String correlationId) {
        return OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .eventType("ORDER_CREATED")
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .orderId(orderId)
                .customerId(customerId)
                .customerEmail(customerEmail)
                .totalAmount(totalAmount)
                .items(items)
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

    public record OrderItemPayload(
            UUID productId,
            String sku,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {}
}
