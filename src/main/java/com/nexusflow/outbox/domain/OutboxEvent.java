package com.nexusflow.outbox.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    public void markAsInProgress() {
        this.status = OutboxStatus.IN_PROGRESS;
        this.claimedAt = OffsetDateTime.now();
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
        this.errorMessage = null;
    }

    public void markAsFailed(String error) {
        this.retryCount++;
        this.errorMessage = error;
        if (this.retryCount >= 5) {
            this.status = OutboxStatus.DEAD;
        } else {
            this.status = OutboxStatus.FAILED;
        }
    }
}
