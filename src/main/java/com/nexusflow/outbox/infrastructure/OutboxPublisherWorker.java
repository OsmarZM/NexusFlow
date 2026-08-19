package com.nexusflow.outbox.infrastructure;

import com.nexusflow.outbox.domain.OutboxEvent;
import com.nexusflow.outbox.domain.OutboxEventRepository;
import com.nexusflow.outbox.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Primary Outbox Poller:
     * Uses PostgreSQL 'SELECT ... FOR UPDATE SKIP LOCKED' to claim batches of events.
     * Prevents race conditions across multiple Kubernetes replica pods (HPA 2-10).
     * Decouples the short database claim transaction from Kafka network latency.
     */
    @Scheduled(fixedDelay = 2000)
    public void processOutboxEvents() {
        List<OutboxEvent> claimedEvents = claimPendingEvents();

        if (claimedEvents.isEmpty()) {
            return;
        }

        log.debug("Claimed {} outbox events for publishing to Kafka", claimedEvents.size());

        for (OutboxEvent event : claimedEvents) {
            publishSingleEvent(event);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claimPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findPendingEventsForProcessingWithLock(50);
        for (OutboxEvent event : events) {
            event.markAsInProgress();
        }
        return outboxRepository.saveAll(events);
    }

    private void publishSingleEvent(OutboxEvent event) {
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    event.getTopic(),
                    event.getAggregateId(),
                    event.getPayload()
            );
            record.headers().add("correlationId", event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

            // Synchronous send with timeout ensures broker ACK before database state transition
            kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

            updateEventFinalStatus(event.getId(), true, null);
            log.info("Outbox event ID {} successfully published to Kafka topic {}", event.getId(), event.getTopic());
        } catch (Exception e) {
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("Failed to publish outbox event ID {}: {}", event.getId(), errorMsg);
            updateEventFinalStatus(event.getId(), false, errorMsg);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateEventFinalStatus(UUID eventId, boolean success, String error) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            if (success) {
                event.markAsPublished();
            } else {
                event.markAsFailed(error);
            }
            outboxRepository.save(event);
        });
    }

    /**
     * Lease Recovery Worker:
     * Recovers any events stuck in IN_PROGRESS (e.g. if a pod crashed during Kafka send)
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverStuckEvents() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(2);
        List<OutboxEvent> stuckEvents = outboxRepository.findStuckEvents(OutboxStatus.IN_PROGRESS, threshold);
        if (!stuckEvents.isEmpty()) {
            log.warn("Found {} stuck IN_PROGRESS outbox events. Resetting to PENDING for retry.", stuckEvents.size());
            for (OutboxEvent event : stuckEvents) {
                event.setStatus(OutboxStatus.PENDING);
            }
            outboxRepository.saveAll(stuckEvents);
        }
    }
}
