package com.nexusflow.outbox.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC")
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(@Param("status") OutboxStatus status, Pageable pageable);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status IN (:statuses) ORDER BY o.createdAt ASC")
    List<OutboxEvent> findByStatusInOrderByCreatedAtAsc(@Param("statuses") List<OutboxStatus> statuses, Pageable pageable);

    @Query(value = "SELECT * FROM outbox_events WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingEventsForProcessingWithLock(@Param("limit") int limit);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.createdAt < :threshold")
    List<OutboxEvent> findStuckEvents(@Param("status") OutboxStatus status, @Param("threshold") OffsetDateTime threshold);
}
