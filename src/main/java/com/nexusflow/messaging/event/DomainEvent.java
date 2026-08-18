package com.nexusflow.messaging.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();
    Instant getOccurredAt();
    String getEventType();
    String getCorrelationId();
}
