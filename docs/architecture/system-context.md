# 🏛️ NexusFlow Architecture Context & Design Document

## 1. System Overview
**NexusFlow** is a distributed enterprise order management, inventory locking, and payment orchestration platform designed for high-concurrency environments.

```mermaid
C4Context
    title System Context diagram for NexusFlow Platform

    Person(customer, "Client / ERP / Marketplace", "External sales channels and client applications consuming the REST API.")
    System(nexusflow, "NexusFlow Core Platform", "Modular monolith backend providing authentication, inventory reservation, order lifecycle, saga orchestration, and outbox messaging.")
    
    SystemDb(postgres, "PostgreSQL 16", "ACID transactional relational persistence, Flyway schema migrations, and Outbox table.")
    SystemDb(redis, "Redis 7", "Distributed secondary cache and sliding-window rate limiter.")
    SystemQueue(kafka, "Apache Kafka (KRaft)", "Event backbone for asynchronous events, Saga step orchestration, and event-driven decoupling.")
    System_Ext(prometheus, "Prometheus & Grafana", "Metrics collection and real-time visualization dashboards.")

    Rel(customer, nexusflow, "Submits orders, requests catalog, simulates payments", "HTTPS / REST / JWT")
    Rel(nexusflow, postgres, "Reads/writes entities & outbox events with Pessimistic Locking", "JDBC / JPA")
    Rel(nexusflow, redis, "Caches catalog and checks rate limits", "RESP Protocol")
    Rel(nexusflow, kafka, "Publishes and consumes domain events", "Kafka Protocol / TCP")
    Rel(prometheus, nexusflow, "Scrapes metrics from /actuator/prometheus", "HTTP")
```

---

## 2. Module Boundaries & Responsibilities

| Module | Core Responsibility | Key Entities & Components |
| :--- | :--- | :--- |
| **Security & Auth** | Stateless JWT authentication, role management, and endpoint authorization | `User`, `Role`, `JwtService`, `SecurityConfig` |
| **Customer** | Customer profiles, status lifecycle, validation | `Customer`, `CustomerRepository`, `CustomerService` |
| **Product & Catalog** | SKU management, pricing, Redis 2nd-level caching | `Product`, `ProductService`, `@Cacheable` |
| **Inventory** | Stock balances, warehouse allocations, Pessimistic Locking (`SELECT FOR UPDATE`) | `Inventory`, `InventoryReservation`, `InventoryService` |
| **Order Management** | Order creation, validation, pricing calculations, item reservations | `Order`, `OrderItem`, `OrderService` |
| **Payment** | Asynchronous payment gateway simulation, transactional outbox emission | `Payment`, `PaymentService`, `PaymentController` |
| **Saga Orchestrator** | Distributed transaction coordinator, payment approval, compensating transactions | `OrderSagaOrchestrator`, `CompensationWorker` |
| **Transactional Outbox** | Guaranteed at-least-once event publication to Kafka | `OutboxEvent`, `OutboxService`, `OutboxPublisherWorker` |
| **Rate Limiter** | Token bucket / Sliding-window distributed traffic throttling | `RateLimiterService`, `RateLimitFilter` |
| **Observability** | Prometheus business metrics and OpenTelemetry tracing | `BusinessMetricsService`, OpenTelemetry bridge |

---

## 3. Distributed Saga State Machine

```mermaid
stateDiagram-v2
    [*] --> WAITING_PAYMENT : Order Created & Stock Reserved
    
    WAITING_PAYMENT --> PAID : Payment Processed (SUCCESS)
    WAITING_PAYMENT --> CANCELLED : Payment Processed (FAILURE)

    PAID --> FULFILLED : Stock physically deducted & ready for dispatch
    CANCELLED --> [*] : Compensating Transaction: Stock Reservation Released
    FULFILLED --> DELIVERED : Shipping Confirmed
    DELIVERED --> [*]
```
