# ADR-001: Adoption of Modular Monolith Architecture

## Status
Accepted

## Context
When architecting enterprise backend systems like an Order Management and Fulfillment Platform, the initial instinct is often to immediately deploy 6 to 8 microservices (Order, Inventory, Payment, Customer, Shipping, Notification, Audit). 

However, in the initial stages of a product:
1. Network boundaries between distributed microservices add significant latency, operational complexity (distributed logging, service discovery, cross-service transactions, network failures).
2. Refactoring domain boundaries across multiple repositories and deployments is slow and costly.
3. Microservices without mature domain boundaries often degenerate into distributed monoliths.

## Decision
We chose to structure **NexusFlow** as a **Modular Monolith** using Java 17 and Spring Boot 3.

Key architectural boundaries:
- Strict **package-by-feature** separation (`com.nexusflow.order`, `com.nexusflow.inventory`, `com.nexusflow.customer`, `com.nexusflow.product`, `com.nexusflow.shared`).
- Domain-driven encapsulation: modules expose well-defined application services and DTOs.
- Clear migration path: independent domain aggregates enable straightforward extraction into isolated microservices in future iterations (V2+) when scaling or deployment requirements demand it.

## Consequences
### Positive
- Single deployable artifact with minimal operational overhead.
- High developer velocity and fast feedback loops during development and testing.
- Local transactions and in-memory calls for low latency, while maintaining strict domain boundaries.
- Seamless transition to Event-Driven Architecture (Kafka) without changing business core logic.

### Negative / Trade-offs
- All modules share the same runtime process and memory space initially.
- Requires team discipline and architecture validation (e.g., ArchUnit) to prevent accidental circular package dependencies.
