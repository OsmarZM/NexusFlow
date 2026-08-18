package com.nexusflow.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusflow.messaging.event.DomainEvent;
import com.nexusflow.outbox.domain.OutboxEvent;
import com.nexusflow.outbox.domain.OutboxEventRepository;
import com.nexusflow.outbox.domain.OutboxStatus;
import com.nexusflow.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent saveEvent(String aggregateType, UUID aggregateId, String topic, DomainEvent event) {
        log.debug("Saving outbox event {} for aggregate {} [{}]", event.getEventType(), aggregateType, aggregateId);

        try {
            String payloadJson = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId.toString())
                    .eventType(event.getEventType())
                    .topic(topic)
                    .payload(payloadJson)
                    .correlationId(event.getCorrelationId())
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();

            OutboxEvent saved = outboxRepository.save(outboxEvent);
            log.info("Outbox event saved successfully with ID: {}", saved.getId());
            return saved;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize domain event: {}", e.getMessage(), e);
            throw new BusinessException("Failed to serialize domain event for outbox", e);
        }
    }
}
