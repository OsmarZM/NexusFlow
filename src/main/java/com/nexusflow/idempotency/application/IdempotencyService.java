package com.nexusflow.idempotency.application;

import com.nexusflow.idempotency.domain.ProcessedEvent;
import com.nexusflow.idempotency.domain.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(UUID eventId, String consumerName) {
        return processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsProcessed(UUID eventId, String eventType, String consumerName) {
        log.debug("Marking event {} as processed by consumer {}", eventId, consumerName);
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .consumerName(consumerName)
                .build();
        processedEventRepository.save(processedEvent);
    }
}
