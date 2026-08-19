package com.nexusflow.outbox.infrastructure;

import com.nexusflow.outbox.domain.OutboxEvent;
import com.nexusflow.outbox.domain.OutboxEventRepository;
import com.nexusflow.outbox.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> eligibleEvents = outboxRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                PageRequest.of(0, 50)
        );

        if (eligibleEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} eligible outbox events to publish to Kafka", eligibleEvents.size());

        for (OutboxEvent event : eligibleEvents) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        event.getTopic(),
                        event.getAggregateId(),
                        event.getPayload()
                );
                record.headers().add("correlationId", event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

                // Synchronous send with timeout guarantees Kafka broker ACK before committing status
                kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

                event.markAsPublished();
                outboxRepository.save(event);
                log.info("Outbox event ID {} successfully published to Kafka topic {}", event.getId(), event.getTopic());
            } catch (Exception e) {
                String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                log.error("Failed to publish outbox event ID {}: {}", event.getId(), errorMsg);
                event.markAsFailed(errorMsg);
                outboxRepository.save(event);
            }
        }
    }
}
