package com.nexusflow.outbox.infrastructure;

import com.nexusflow.outbox.domain.OutboxEvent;
import com.nexusflow.outbox.domain.OutboxEventRepository;
import com.nexusflow.outbox.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dedicated Transactional Coordinator for Outbox Event Processing.
 * <p>
 * Ensures all database transactions are intercepted by Spring's AOP Proxy
 * (avoiding @Transactional self-invocation issues) and guarantees short, isolated
 * database transactions with PostgreSQL 'SELECT ... FOR UPDATE SKIP LOCKED'.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxTransactionCoordinator {

    private final OutboxEventRepository outboxRepository;

    /**
     * Atomically locks and claims a batch of pending/failed events using PostgreSQL SKIP LOCKED.
     * Sets status to IN_PROGRESS and stamps claimed_at for lease tracking.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claimPendingBatch(int limit) {
        List<OutboxEvent> events = outboxRepository.findPendingEventsForProcessingWithLock(limit);
        for (OutboxEvent event : events) {
            event.markAsInProgress();
        }
        return outboxRepository.saveAll(events);
    }

    /**
     * Marks an event as successfully published and commits immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.markAsPublished();
            outboxRepository.save(event);
        });
    }

    /**
     * Records a publishing failure, increments retry count and sets FAILED or DEAD (DLQ).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String error) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.markAsFailed(error);
            outboxRepository.save(event);
        });
    }

    /**
     * Recovers orphaned events stuck in IN_PROGRESS (e.g. if a pod crashed during Kafka send)
     * by checking if claimed_at exceeds the lease threshold.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStuckEvents(OffsetDateTime threshold) {
        List<OutboxEvent> stuckEvents = outboxRepository.findStuckEvents(OutboxStatus.IN_PROGRESS, threshold);
        if (!stuckEvents.isEmpty()) {
            log.warn("Found {} stuck IN_PROGRESS outbox events beyond lease timeout. Resetting to PENDING.", stuckEvents.size());
            for (OutboxEvent event : stuckEvents) {
                event.setStatus(OutboxStatus.PENDING);
                event.setClaimedAt(null);
            }
            outboxRepository.saveAll(stuckEvents);
            return stuckEvents.size();
        }
        return 0;
    }
}
