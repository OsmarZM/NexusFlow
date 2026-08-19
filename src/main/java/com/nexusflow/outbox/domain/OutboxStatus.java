package com.nexusflow.outbox.domain;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    FAILED,
    DEAD
}
