package com.nexusflow.outbox.infrastructure;

import com.nexusflow.outbox.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

    private final OutboxTransactionCoordinator transactionCoordinator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Primary Outbox Poller:
     * Claims events via OutboxTransactionCoordinator (using PostgreSQL SKIP LOCKED).
     * Decouples the short database claim transaction from Kafka network latency.
     */
    @Scheduled(fixedDelay = 2000)
    public void processOutboxEvents() {
        List<OutboxEvent> claimedEvents = transactionCoordinator.claimPendingBatch(50);

        if (claimedEvents.isEmpty()) {
            return;
        }

        log.debug("Claimed {} outbox events for publishing to Kafka", claimedEvents.size());

        for (OutboxEvent event : claimedEvents) {
            publishSingleEvent(event);
        }
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

            transactionCoordinator.markPublished(event.getId());
            log.info("Outbox event ID {} successfully published to Kafka topic {}", event.getId(), event.getTopic());
        } catch (Exception e) {
            String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("Failed to publish outbox event ID {}: {}", event.getId(), errorMsg);
            transactionCoordinator.markFailed(event.getId(), errorMsg);
        }
    }

    /**
     * Lease Recovery Worker:
     * Recovers any events stuck in IN_PROGRESS beyond the 2-minute lease threshold.
     */
    @Scheduled(fixedDelay = 60000)
    public void recoverStuckEvents() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(2);
        transactionCoordinator.recoverStuckEvents(threshold);
    }
}
