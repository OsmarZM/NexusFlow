# ADR-010: Testcontainers for Real Infrastructure Integration Testing

## Status
Accepted

## Context
Mocking database repositories, Redis clients, and Kafka message brokers with Mockito or relying on in-memory databases (like H2) presents serious risks:
- H2 does not support PostgreSQL-specific dialect features, locking semantics (`SELECT ... FOR UPDATE`), check constraints, and JSONB operators.
- Embedded Kafka or in-memory mocks fail to catch real network serialization, partition key routing, or concurrency race conditions.

To achieve production-grade confidence, tests must execute against the exact same engine binaries used in production.

## Decision
We adopted **Testcontainers (JUnit 5)** for automated integration and E2E testing.

Architecture:
1. **AbstractIntegrationTest Base Class:** Declares static Docker containers for **PostgreSQL 16**, **Redis 7**, and **Apache Kafka**.
2. **`@DynamicPropertySource`:** Dynamically overrides Spring application properties (`spring.datasource.url`, `spring.kafka.bootstrap-servers`, `spring.data.redis.host/port`) to point to ephemeral container ports allocated by Docker.
3. **Multi-Level Testing Strategy:**
   - **Unit Tests:** Fast, isolated unit verification with Mockito.
   - **Integration Tests:** Realistic verification against containerized PostgreSQL, Kafka, and Redis instances.
   - **CI/CD Integration:** Automated execution via GitHub Actions (`mvn clean verify`).

## Consequences
### Positive
- 100% parity between local test suite, CI/CD pipeline, and production infrastructure.
- Zero mock drift or false-positive tests with SQL dialect mismatches.

### Negative / Trade-offs
- Integration tests require a running Docker daemon and take slightly longer to boot on cold start.
