# ADR-008: Consumer Idempotency and Deduplication

## Status
Accepted

## Context
Message brokers guaranteeing at-least-once delivery (such as Apache Kafka and Transactional Outbox workers) may deliver the same event multiple times due to:
- Network timeouts during consumer ACK / offset commit.
- Consumer process restarts during event processing.
- Broker leader rebalance.

If an event like `PaymentProcessedEvent` or `OrderCreatedEvent` is processed twice, dangerous side effects can occur: charging a customer twice, double-deducting physical stock, or generating duplicate shipping labels.

## Decision
We implemented a **Database-Backed Idempotency Filter** (`IdempotencyService`).

Implementation mechanism:
1. Every domain event contains an immutable `UUID eventId`.
2. Consumers store a record in the `processed_events` table with composite primary key `(event_id, consumer_name)` upon successful processing.
3. Before executing business logic, consumers query `existsByEventIdAndConsumerName(eventId, consumerName)`.
4. If a duplicate message arrives, it is logged and immediately acknowledged without re-executing state transitions.

## Consequences
### Positive
- Strict idempotent execution across all distributed event consumers.
- Complete protection against duplicate financial charges and stock corruption.

### Negative / Trade-offs
- Adds a fast indexed database lookup before each message processing step.
