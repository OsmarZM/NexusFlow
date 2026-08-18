# ADR-006: Transactional Outbox Pattern for Dual-Write Consistency

## Status
Accepted

## Context
When persisting an entity to the database (e.g. `orders` or `payments`) and simultaneously publishing an event to Apache Kafka, systems encounter the classic **Dual-Write Problem**:
- If the database commit succeeds but the network/Kafka publish fails, the event is permanently lost.
- If the Kafka publish succeeds but the database rollback occurs, downstream consumers process ghost data.

Distributed 2PC (Two-Phase Commit / XA) transactions across heterogeneous storage (Postgres and Kafka) are notoriously slow, fragile, and not supported by Kafka brokers.

## Decision
We implemented the **Transactional Outbox Pattern**.

Key implementation details:
1. **Atomic Local Transaction:** Whenever an aggregate emits an integration event, the event payload is serialized to JSON and inserted into an `outbox_events` table inside the *exact same* ACID database transaction as the business entity.
2. **Asynchronous Polling Publisher:** The `OutboxPublisherWorker` runs a scheduled polling loop (`@Scheduled(fixedDelay = 2000)`), queries for `PENDING` records in FIFO order (`ORDER BY created_at ASC`), publishes them to Kafka via `KafkaTemplate`, and atomically transitions their state to `PUBLISHED`.
3. **Retry & Backoff:** Transient publisher failures increment `retry_count` and log the error, retaining the record for subsequent retries to achieve guaranteed **At-Least-Once Delivery**.

## Consequences
### Positive
- Total transactional consistency without 2PC or distributed locks.
- Zero loss of domain events even if the message broker is temporarily unreachable.

### Negative / Trade-offs
- Slight latency between database commit and Kafka delivery (typically 1-2 seconds with polling worker).
- Downstream consumers must handle potential duplicate message delivery (addressed by ADR-008: Consumer Idempotency).
