# 🚀 NexusFlow — Distributed Order Management & Fulfillment Platform

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=for-the-badge&logo=flyway&logoColor=white)](https://flywaydb.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

> **Distributed Order Management & Fulfillment Platform** built with **Java 17**, **Spring Boot 3**, **PostgreSQL**, **Redis**, **Apache Kafka**, **Testcontainers**, **OpenTelemetry**, and **Kubernetes**. Designed to solve real-world distributed systems challenges: concurrent inventory locking, saga compensation, transactional outbox, and idempotent event processing.

---

## 📑 Table of Contents
- [The Problem & Business Scenario](#-the-problem--business-scenario)
- [Architecture & Evolution](#-architecture--evolution)
- [Key Engineering Challenges Solved](#-key-engineering-challenges-solved)
- [Tech Stack](#-tech-stack)
- [Architecture Decision Records (ADRs)](#-architecture-decision-records-adrs)
- [REST API Reference](#-rest-api-reference)
- [Running Locally](#-running-locally)
- [Testing & Quality](#-testing--quality)
- [Roadmap & Releases](#-roadmap--releases)

---

## 🎯 The Problem & Business Scenario

In modern retail and omnichannel commerce, receiving orders from multiple sources (ERPs, Marketplaces, Web Apps) requires a robust backend capable of:
1. **Handling concurrent checkouts without overselling** (e.g. 100 simultaneous requests for the last 2 GPU units).
2. **Coordinating multi-step transactions across domains** (Order -> Inventory -> Payment -> Shipping).
3. **Guaranteeing eventual consistency & compensations** when external dependencies fail (e.g. payment rejected triggers inventory release).
4. **Preventing duplicate processing** caused by network retries or message broker replays.
5. **Observing distributed latencies & bottlenecks** across all system components.

---

## 🏛️ Architecture & Evolution

NexusFlow was engineered as a **Modular Monolith** with a direct evolutionary path towards full **Event-Driven Microservices**.

```
                           +------------------------+
                           |  Client / ERP / Store  |
                           +-----------+------------+
                                       |
                                       v
                           +------------------------+
                           |     REST API Gateway   |
                           +-----------+------------+
                                       |
                   +-------------------+-------------------+
                   |                                       |
                   v                                       v
         +-------------------+                   +-------------------+
         |  Customer Domain  |                   |  Product Catalog  |
         +-------------------+                   +-------------------+
                   |                                       |
                   +-------------------+-------------------+
                                       |
                                       v
                           +------------------------+
                           |     Order Domain       |
                           |  (Aggregate Root)      |
                           +-----------+------------+
                                       |
                     [Pessimistic / Optimistic Locking]
                                       v
                           +------------------------+
                           |    Inventory Domain    |
                           |  (Stock Reservations)  |
                           +-----------+------------+
                                       |
                                       v
                           +------------------------+
                           |  PostgreSQL 16 Engine  |
                           |  (Flyway Migrations)   |
                           +------------------------+
```

### Why Modular Monolith first?
Starting with 8 isolated microservices produces high operational latency, network fragmentation, and unnecessary boilerplate. NexusFlow enforces strict **Package-by-Feature / Clean Architecture** boundaries inside a single deployable runtime, allowing seamless extraction of high-throughput services (like Inventory & Orders) into independent microservices when required.

---

## 🧠 Key Engineering Challenges Solved

| Challenge | Solution Pattern | Implementation Details |
|---|---|---|
| **Concurrent Overselling** | Optimistic & Pessimistic Row Locking | `@Version` + `SELECT ... FOR UPDATE` + DB check constraints |
| **Schema Drift in Production** | Versioned Migrations & DDL Validation | Flyway SQL migrations with Hibernate `ddl-auto: validate` |
| **Domain Decoupling** | Package by Feature / Hexagonal Ports | Pure domain models, isolation of infrastructure concerns |
| **Error Transparency** | RFC 7807 Problem Details | Global REST advice returning standardized problem details |
| **Dual-Write Consistency** | Transactional Outbox Pattern | Outbox table inside the same ACID transaction (*Phase V0.5*) |
| **Distributed Failures** | Saga Choreography & Compensations | Event-based rollback of stock reservations (*Phase V0.5*) |
| **Message Deduplication** | Consumer Idempotency | `processed_events` tracking table (*Phase V0.5*) |

---

## 🛠️ Tech Stack

- **Core:** Java 17 (OpenJDK Temurin), Spring Boot 3.3.5, Spring MVC, Spring Data JPA, Spring Validation, Spring Actuator.
- **Database & Persistence:** PostgreSQL 16, Flyway Migrations, HikariCP Connection Pool.
- **Caching & Messaging (Upcoming):** Redis, Apache Kafka, KafkaTemplate, Consumer Idempotency.
- **Testing:** JUnit 5, Mockito, AssertJ, Testcontainers (Real PostgreSQL, Redis, Kafka).
- **API Documentation:** Springdoc OpenAPI (Swagger UI v3).
- **Observability (Upcoming):** Micrometer, Prometheus, Grafana, OpenTelemetry.
- **Infrastructure:** Docker, Docker Compose, Kubernetes manifests.

---

## 📜 Architecture Decision Records (ADRs)

Key architectural decisions are documented with full context and trade-offs:
- [ADR-001: Adoption of Modular Monolith Architecture](docs/adr/001-modular-monolith-architecture.md)
- [ADR-002: PostgreSQL and Flyway for Relational Persistence and Migration](docs/adr/002-postgresql-and-flyway-for-persistence.md)
- [ADR-003: Concurrency Control and Stock Reservation Strategy](docs/adr/003-concurrency-and-locking-strategy.md)

---

## 🌐 REST API Reference

Interactive API documentation is available via **Swagger UI** at `http://localhost:8080/swagger-ui.html`.

### Customers (`/api/v1/customers`)
- `POST /api/v1/customers` — Register a customer (name, email, document).
- `GET /api/v1/customers/{id}` — Get customer details.
- `GET /api/v1/customers` — List paginated customers.
- `PUT /api/v1/customers/{id}` — Update customer profile.
- `DELETE /api/v1/customers/{id}` — Deactivate customer.

### Products (`/api/v1/products`)
- `POST /api/v1/products` — Create product (SKU, name, price, initialStock).
- `GET /api/v1/products/{id}` — Get product by ID.
- `GET /api/v1/products/sku/{sku}` — Get product by SKU.
- `GET /api/v1/products` — List paginated products.

### Inventory (`/api/v1/inventory`)
- `GET /api/v1/inventory/{sku}` — Get physical, reserved, and available stock.
- `POST /api/v1/inventory/{sku}/replenish` — Replenish physical warehouse stock.
- `POST /api/v1/inventory/reservations` — Reserve stock for an order (supports optimistic/pessimistic lock).
- `DELETE /api/v1/inventory/reservations/orders/{orderId}/skus/{sku}` — Release reserved stock (compensation).
- `POST /api/v1/inventory/reservations/orders/{orderId}/skus/{sku}/confirm` — Confirm stock deduction.

### Orders (`/api/v1/orders`)
- `POST /api/v1/orders` — Create order (validates customer, checks and reserves stock for each item).
- `GET /api/v1/orders/{id}` — Get order details with items and subtotals.
- `GET /api/v1/orders/customer/{customerId}` — List orders by customer.
- `POST /api/v1/orders/{id}/cancel` — Cancel order and release reserved stock.
- `POST /api/v1/orders/{id}/pay` — Simulate payment confirmation and confirm stock deduction.

---

## ⚡ Running Locally

### 1. Prerequisites
- Java 17+
- Docker & Docker Compose

## 🚀 Como Executar o Projeto com 1 Único Comando

Criamos um script que automatiza tudo (abre o Docker Desktop se estiver fechado, sobe o PostgreSQL 16, Redis 7, Kafka, Prometheus, Grafana, aguarda o banco ficar pronto e inicia a aplicação Spring Boot):

```powershell
.\start.ps1
```
*Ou simplesmente dê dois cliques no arquivo `start.bat`.*

---

### Execução Manual por Etapas (Opcional):

#### 1. Iniciar a Infraestrutura com Docker Compose
```bash
docker compose -f docker/docker-compose.yml up -d
```
Serviços iniciados:
- **PostgreSQL 16**: `localhost:5433` (isolado para não colidir com Postgres local)
- **Redis 7**: `localhost:6379`
- **Apache Kafka (KRaft)**: `localhost:9092`
- **Prometheus**: `http://localhost:9090`
- **Grafana**: `http://localhost:3000` (admin / admin)
- **pgAdmin**: `http://localhost:5050` (admin@nexusflow.com / admin)

#### 2. Iniciar a Aplicação Spring Boot
```bash
.\mvnw.cmd spring-boot:run
```

### 4. Explore Endpoints
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **OpenAPI JSON Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🧪 Testing & Quality

Execute all automated unit and integration tests:
```powershell
.\mvnw.cmd test
```

---

## 🗺️ Roadmap & Releases

- [x] **V0.1 — Core Modular Monolith:** Customers, Products, Inventory (Locking), Orders, Flyway, PostgreSQL, OpenAPI & ADRs.
- [x] **V0.2 — Security & RBAC:** Spring Security 6, Stateless JWT, Role-based method security (`ADMIN`, `WAREHOUSE_OPERATOR`, `FINANCE`, `CUSTOMER`), Seeded Administrator.
- [x] **V0.3 — Concurrency & High-Load Testing:** Multithreaded race condition simulation, CountDownLatch test suite, guaranteed zero overselling.
- [x] **V0.4 — Event-Driven Architecture:** Apache Kafka integration (KRaft mode), strongly-typed domain events (`OrderCreatedEvent`, `OrderCancelledEvent`, `PaymentRequestedEvent`), partition ordering, and correlation headers.
- [x] **V0.5 — Distributed Resilience:** Transactional Outbox Pattern, Saga Choreography, Compensating Transactions (payment failure triggers stock release), and Idempotent Consumers.
- [x] **V0.6 — Performance & Caching:** Redis 7 2nd-level caching (`@Cacheable`, `@CacheEvict`), Distributed Token Bucket / Sliding Window Rate Limiting (HTTP 429 RFC 7807) with fail-open fallback.
- [x] **V0.7 — Testing Excellence & CI/CD:** Testcontainers for PostgreSQL 16, Kafka, Redis; Multi-stage Dockerfile; GitHub Actions automated CI/CD pipeline (`mvn clean verify`).
- [x] **V1.0 — Observability & Cloud Native:** Micrometer Prometheus metrics, OpenTelemetry distributed tracing (W3C trace context), Prometheus & Grafana docker orchestration, and production Kubernetes manifests (`k8s/` Deployment, Service, ConfigMap, Secret, HPA autoscaler).

---

## 👨‍💻 Author & Engineering Philosophy
Developed as a showcase of enterprise backend engineering in Java & Spring Boot, applying clean code, domain driven design principles, distributed system patterns, and production-grade resilience.
