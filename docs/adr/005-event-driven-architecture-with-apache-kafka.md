# ADR-005: Event-Driven Architecture (EDA) with Apache Kafka

## Status
Accepted

## Context
As NexusFlow scales, synchronous HTTP REST communication between domains (e.g. Order synchronously waiting for Payment processing, Inventory confirmation, Notification dispatch, and Shipping labels) introduces high coupling, cascading latency, and single points of failure.

If an external payment gateway or warehouse system experiences downtime, synchronous requests block worker threads and fail the entire user checkout experience.

## Decision
We adopted **Apache Kafka** as our distributed event backbone and implemented an **Event-Driven Architecture (EDA)**.

Key design principles:
1. **Domain Events:** State changes emit strongly typed events (`OrderCreatedEvent`, `InventoryReservedEvent`, `OrderCancelledEvent`, `PaymentRequestedEvent`, `PaymentProcessedEvent`).
2. **Partition Keys:** Events use the aggregate identifier (`orderId.toString()`) as the Kafka message key. This guarantees total ordering for all lifecycle events belonging to the same order within a specific partition.
3. **Traceability & Correlation IDs:** Each event carries `correlationId`, `eventId`, and `eventType` in custom Kafka record headers to allow end-to-end distributed tracing.
4. **Decoupled Asynchronous Processing:** Downstream consumers (notifications, shipping, analytics, saga orchestrators) consume events reactively without affecting the core API response time.

## Consequences
### Positive
- Loose coupling between business domains.
- High resilience and temporal decoupling: messages persist in Kafka brokers even if consumers are temporarily down.
- Massive horizontal throughput and replay capability.

### Negative / Trade-offs
- Introduces eventual consistency into the system.
- Requires robust consumer idempotency and error handling (DLQ / retry topics) to handle network retries.
