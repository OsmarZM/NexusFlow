# ADR-011: Distributed Observability with OpenTelemetry, Prometheus, and Grafana

## Status
Accepted

## Context
In a distributed event-driven system where requests propagate across multiple layers (REST Gateway -> Database Transaction -> Outbox Worker -> Kafka Topic -> Saga Orchestrator -> Downstream Listeners), troubleshooting latency bottlenecks, error spikes, or consumer lag with traditional text logs is ineffective.

We require:
1. Standardized distributed tracing with unique Trace IDs and Span IDs propagated across HTTP and Kafka headers.
2. High-resolution time-series metrics (P95/P99 latency, throughput, error rates, Kafka lag).
3. Live operational dashboards for real-time monitoring.

## Decision
We implemented a complete observability stack leveraging **Micrometer Observation**, **OpenTelemetry**, **Prometheus**, and **Grafana**:
1. **W3C Distributed Tracing:** Configured Micrometer Tracing with OpenTelemetry bridge to inject `traceId` and `spanId` into MDC logs and Kafka record headers.
2. **Business & System Metrics:** Exported system JVM, HikariCP, and custom business metrics (`nexusflow.orders.created.total`, `nexusflow.payments.failed.total`, `nexusflow.inventory.reservations.total`) via `/actuator/prometheus`.
3. **Prometheus & Grafana Ingestion:** Prometheus periodically scrapes the Spring Boot Actuator endpoint every 5 seconds, powering Grafana dashboards for immediate visual inspection.

## Consequences
### Positive
- End-to-end visibility into distributed request lifecycles.
- Immediate detection of slow database queries or saturated connection pools.

### Negative / Trade-offs
- Slight CPU and network overhead for trace export (mitigated in production by configurable sampling rates, e.g. 10% - 50%).
