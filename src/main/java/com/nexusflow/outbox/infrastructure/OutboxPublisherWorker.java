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

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to publish to Kafka", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        event.getTopic(),
                        event.getAggregateId(),
                        event.getPayload()
                );
                record.headers().add("correlationId", event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

                kafkaTemplate.send(record).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish outbox event ID {}: {}", event.getId(), ex.getMessage());
                        event.markAsFailed(ex.getMessage());
                    } else {
                        event.markAsPublished();
                        outboxRepository.save(event);
                        log.info("Outbox event ID {} successfully published to Kafka topic {}", event.getId(), event.getTopic());
                    }
                });
            } catch (Exception e) {
                log.error("Exception occurred publishing outbox event ID {}: {}", event.getId(), e.getMessage(), e);
                event.markAsFailed(e.getMessage());
                outboxRepository.save(event);
            }
        }
    }
}
