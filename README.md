# 🚀 NexusFlow — Enterprise Distributed Order, Inventory & Logistics Platform

<div align="center">

![NexusFlow Banner](docs/assets/nexusflow_banner.jpg)

**Plataforma corporativa distribuída para processamento de pedidos em tempo real, controle atômico de estoque, pagamentos assíncronos e orquestração de microsserviços.**

[![Java](https://img.shields.io/badge/Java-17-orange.svg?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-brightgreen.svg?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg?style=for-the-badge&logo=redis)](https://redis.io/)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-3.7-black.svg?style=for-the-badge&logo=apachekafka)](https://kafka.apache.org/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Ready-326CE5.svg?style=for-the-badge&logo=kubernetes)](https://kubernetes.io/)
[![Prometheus](https://img.shields.io/badge/Prometheus-Monitoring-E6522C.svg?style=for-the-badge&logo=prometheus)](https://prometheus.io/)
[![Grafana](https://img.shields.io/badge/Grafana-Dashboards-F46800.svg?style=for-the-badge&logo=grafana)](https://grafana.com/)

</div>

---

## 📸 Visão Geral da Interface & Monitoramento

<div align="center">

### 🖥️ Painel Operacional em Tempo Real
![NexusFlow Dashboard UI](docs/assets/nexusflow_dashboard.jpg)

### 📊 Observabilidade & Telemetria Distribuída (Prometheus + Grafana + OpenTelemetry)
![NexusFlow Observability UI](docs/assets/nexusflow_observability.jpg)

</div>

---

## 🏛️ Arquitetura do Sistema

O NexusFlow foi concebido segundo os princípios de **Modular Monolith** com preparação para transição a microsserviços, integrando padrões avançados de sistemas distribuídos:

```mermaid
graph TB
    subgraph ClientLayer["Clients & Gateway Layer"]
        ERP["🏢 ERP / Marketplace"]
        SPA["💻 Web / Mobile Apps"]
        GW["🛡️ RateLimitFilter & Security (JWT)"]
    end

    subgraph CoreEngine["NexusFlow Core Engine (Spring Boot 3)"]
        AUTH["🔑 Auth & RBAC Module"]
        PROD["📦 Product Catalog (Redis Cache)"]
        INV["🛡️ Inventory Management (Pessimistic Lock)"]
        ORD["🛒 Order Orchestrator & Validation"]
        OUTBOX["📬 Transactional Outbox Worker"]
        SAGA["⚡ Saga Orchestrator (Compensating TX)"]
        PAY["💳 Payment Simulator Service"]
        METRICS["📈 Business Metrics (Micrometer/OTel)"]
    end

    subgraph DataLayer["Storage & Cache Layer"]
        PG[("🐘 PostgreSQL 16 (Port 5433)\nACID Storage & Outbox")]
        REDIS[("⚡ Redis 7 (Port 6379)\nDistributed Cache & Rate Limit")]
    end

    subgraph EventMesh["Event Streaming Mesh"]
        KAFKA{{"🌪️ Apache Kafka (KRaft Mode)\nTopics: orders.created, payments.processed"}}
    end

    subgraph Observability["Observability Mesh"]
        PROM["🔥 Prometheus (Port 9090)"]
        GRAF["📊 Grafana (Port 3000)"]
    end

    ERP -->|"HTTP + JWT"| GW
    SPA -->|"HTTP + JWT"| GW
    GW --> AUTH
    GW --> PROD
    GW --> ORD

    PROD <-->|"Cache Hit / Invalidate"| REDIS
    PROD <-->|"CRUD Data"| PG

    ORD -->|"1. Validate & Reserve Stock"| INV
    INV <-->|"Pessimistic Lock FOR UPDATE"| PG
    ORD -->|"2. Create Order & Write Outbox"| PG

    OUTBOX <-->|"Poll PENDING Events"| PG
    OUTBOX -->|"3. Publish Event"| KAFKA

    KAFKA -->|"4. Consume Event"| SAGA
    SAGA -->|"5. Trigger Payment"| PAY
    PAY -->|"6. Payment Result"| OUTBOX
    SAGA -->|"7A. Confirm Stock & Order"| INV
    SAGA -->|"7B. Compensate: Release Stock"| INV

    METRICS -->|"Export Prometheus"| PROM
    PROM -->|"Visualize"| GRAF
```

---

## ⚡ Fluxo de Transação Distribuída (Saga Pattern & Outbox)

```mermaid
sequenceDiagram
    autonumber
    actor Client as 👤 Cliente / ERP
    participant API as 🛡️ NexusFlow API
    participant DB as 🐘 PostgreSQL (ACID)
    participant Outbox as 📬 Outbox Worker
    participant Kafka as 🌪️ Apache Kafka
    participant Saga as ⚡ Saga Orchestrator
    participant Inventory as 📦 Estoque

    Client->>API: POST /api/v1/orders (Criar Pedido)
    activate API
    API->>DB: Iniciar Transação ACID
    API->>DB: SELECT * FROM inventories WHERE sku = ? FOR UPDATE (Pessimistic Lock)
    Note over API,DB: Garante que NENHUM outro thread altere o saldo
    API->>DB: Deduz quantidade reservada & Grava pedido (WAITING_PAYMENT)
    API->>DB: INSERT INTO outbox_events (ORDER_CREATED, PENDING)
    API->>DB: Commit Transação
    API-->>Client: 201 Created (Order ID, Status: WAITING_PAYMENT)
    deactivate API

    loop A cada 1 segundo (Background Worker)
        Outbox->>DB: SELECT * FROM outbox_events WHERE status = 'PENDING'
        Outbox->>Kafka: Publicar em orders.created (Key: OrderId)
        Outbox->>DB: UPDATE outbox_events SET status = 'PUBLISHED'
    end

    Kafka->>Saga: Recebe Evento ORDER_CREATED
    activate Saga
    Saga->>API: Processar Pagamento (POST /api/v1/payments)
    
    alt Pagamento APROVADO
        API->>Kafka: Publica PAYMENT_APPROVED
        Kafka->>Saga: Recebe PAYMENT_APPROVED
        Saga->>DB: Atualiza Pedido para CONFIRMED / PAID
        Saga->>Inventory: Confirma baixa física definitiva no estoque
    else Pagamento REJEITADO (Ex: Falha de Crédito)
        API->>Kafka: Publica PAYMENT_FAILED
        Kafka->>Saga: Recebe PAYMENT_FAILED
        Note over Saga,Inventory: ⚠️ DISPARA TRANSAÇÃO COMPENSATÓRIA
        Saga->>DB: Atualiza Pedido para CANCELLED
        Saga->>Inventory: Libera reserva e devolve unidades ao estoque disponível
    end
    deactivate Saga
```

---

## 🛡️ Controle Atômico de Concorrência (Zero Overselling)

```mermaid
graph TD
    subgraph 50 Concurrent Requests for Last 1 Item
        R1["👤 Requisição 1"]
        R2["👤 Requisição 2"]
        RN["👤 Requisição 50..."]
    end

    LOCK{"🔒 Pessimistic Lock\nSELECT ... FOR UPDATE"}

    R1 -->|Thread 1 Adquire Lock| LOCK
    R2 -->|Thread 2 Aguarda Lock| LOCK
    RN -->|Thread 50 Aguarda Lock| LOCK

    LOCK -->|Saldo > 0| S1["✅ Reserva 1 unidade\nStatus: 201 WAITING_PAYMENT"]
    LOCK -->|Saldo == 0| S2["❌ Estoque Esgotado\nStatus: 409 CONFLICT (RFC 7807)"]
```

---

## 🚀 Como Executar o Projeto com 1 Único Comando

### 🟢 Iniciar Tudo (Docker + Postgres + Redis + Kafka + Prometheus + Grafana + Spring Boot):
```powershell
.\start.ps1
```
*Ou simplesmente dê dois cliques no arquivo `start.bat`.*
> **Gerenciamento Inteligente de Processos:** O script verifica e encerra automaticamente qualquer processo preso na porta `8085` antes de iniciar, garantindo que o sistema sempre suba na mesma porta oficial sem conflito.

---

### 🛑 Encerrar Tudo com Segurança:
```powershell
.\stop.ps1
```
*Ou simplesmente dê dois cliques no arquivo `stop.bat`.*
> Encerra a aplicação Spring Boot e pausa os containers Docker de forma limpa.

---

## 🌐 Endpoints & Painéis Disponíveis

| Serviço / Endpoint | URL | Credenciais / Notas |
| :--- | :--- | :--- |
| **Swagger UI (OpenAPI 3.0)** | [http://localhost:8085/swagger-ui.html](http://localhost:8085/swagger-ui.html) | Documentação interativa de todos os endpoints |
| **Actuator Health & Metrics** | [http://localhost:8085/actuator/health](http://localhost:8085/actuator/health) | Status do Postgres, Redis, Kafka e Probes |
| **Prometheus Metrics** | [http://localhost:8085/actuator/prometheus](http://localhost:8085/actuator/prometheus) | Métricas técnicas e contadores de negócio |
| **Grafana Dashboard** | [http://localhost:3000](http://localhost:3000) | Usuário: `admin` / Senha: `admin` |
| **pgAdmin 4 (PostgreSQL)** | [http://localhost:5050](http://localhost:5050) | Email: `admin@nexusflow.com` / Senha: `admin` |
| **PostgreSQL 16** | `localhost:5433` | Banco: `nexusflow_db` / User: `nexusflow_user` |
| **Redis 7** | `localhost:6379` | Cache e Rate Limiter |
| **Apache Kafka (KRaft)** | `localhost:9092` | Cluster de Mensageria Distribuída |

---

## 🧪 Testes Automatizados de Produção

### 1. Suíte de Testes Unitários & Concorrência (JUnit 5 + Mockito)
```powershell
.\mvnw.cmd test
```
*Total de **32 testes automatizados** cobrindo concorrência com CountDownLatch, Saga Orchestrator, Outbox, Caching e JWT.*

### 2. Teste E2E de Produção ao Vivo (13 Seções)
Com a aplicação em execução, rode:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-e2e-production-flow.ps1
```
*Valida todos os 13 cenários de ponta a ponta: Health Check, Login JWT, Cadastro, Cache Redis, Overselling, Saga Aprovada, Saga Rejeitada com Compensação, Rate Limit e Prometheus.*

---

## 🏛️ Architecture Decision Records (ADRs)

Todas as decisões arquiteturais estão formalmente documentadas em [`docs/adr/`](docs/adr/):

- [`ADR-001`](docs/adr/001-modular-monolith-architecture.md): Modular Monolith Architecture
- [`ADR-002`](docs/adr/002-postgresql-and-flyway-for-persistence.md): PostgreSQL & Flyway for Relational Persistence
- [`ADR-003`](docs/adr/003-concurrency-and-locking-strategy.md): Concurrency & Locking Strategy (Pessimistic vs Optimistic)
- [`ADR-004`](docs/adr/004-stateless-authentication-jwt-rbac.md): Stateless Authentication with Spring Security, JWT & RBAC
- [`ADR-005`](docs/adr/005-event-driven-architecture-with-apache-kafka.md): Event-Driven Architecture with Apache Kafka
- [`ADR-006`](docs/adr/006-transactional-outbox-pattern.md): Transactional Outbox Pattern for Reliable Event Publishing
- [`ADR-007`](docs/adr/007-saga-pattern-and-compensating-transactions.md): Saga Pattern & Compensating Transactions
- [`ADR-008`](docs/adr/008-consumer-idempotency.md): Consumer Idempotency for At-Least-Once Delivery
- [`ADR-009`](docs/adr/009-redis-caching-and-distributed-rate-limiting.md): Redis Caching & Distributed Rate Limiting
- [`ADR-010`](docs/adr/010-testcontainers-and-integration-testing-strategy.md): Testcontainers & Integration Testing Strategy
- [`ADR-011`](docs/adr/011-observability-metrics-tracing-with-opentelemetry-prometheus-grafana.md): Observability with OpenTelemetry, Prometheus & Grafana
- [`ADR-012`](docs/adr/012-kubernetes-deployment-and-horizontal-pod-autoscaling.md): Kubernetes Deployment & Horizontal Pod Autoscaling (HPA)

---

## 🗺️ Roadmap de Entregas Realizadas

- [x] **V0.1 — Core Modular Monolith:** Customers, Products, Inventory (Locking), Orders, Flyway, PostgreSQL, OpenAPI & ADRs.
- [x] **V0.2 — Security & RBAC:** Spring Security 6, Stateless JWT, Role-based method security (`ADMIN`, `WAREHOUSE_OPERATOR`, `FINANCE`, `CUSTOMER`), Seeded Administrator.
- [x] **V0.3 — Concurrency & High-Load Testing:** Multithreaded race condition simulation, CountDownLatch test suite, guaranteed zero overselling.
- [x] **V0.4 — Event-Driven Architecture:** Apache Kafka integration (KRaft mode), strongly-typed domain events, partition ordering, and correlation headers.
- [x] **V0.5 — Distributed Resilience:** Transactional Outbox Pattern, Saga Choreography, Compensating Transactions (payment failure triggers stock release), and Idempotent Consumers.
- [x] **V0.6 — Performance & Caching:** Redis 7 2nd-level caching (`@Cacheable`, `@CacheEvict`), Distributed Token Bucket Rate Limiting (HTTP 429 RFC 7807) with fail-open fallback.
- [x] **V0.7 — Testing Excellence & CI/CD:** Testcontainers for PostgreSQL 16, Kafka, Redis; Multi-stage Dockerfile; GitHub Actions automated CI/CD pipeline (`mvn clean verify`).
- [x] **V1.0 — Observability & Cloud Native:** Micrometer Prometheus metrics, OpenTelemetry distributed tracing, Prometheus & Grafana orchestration, and production Kubernetes manifests (`k8s/` Deployment, Service, ConfigMap, Secret, HPA autoscaler).

---

## 👨‍💻 Autor & Filosofia de Engenharia
Desenvolvido por **Osmar Zanardi Machado** como demonstração de engenharia backend sênior em Java & Spring Boot, aplicando Domain-Driven Design, padrões distribuídos resilientes e observabilidade cloud-native.
