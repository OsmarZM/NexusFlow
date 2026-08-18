# NexusFlow Architecture Context

## Overview
NexusFlow is a high-performance distributed order management and fulfillment platform designed to process orders across multiple sales channels, validate and lock inventory under high concurrency, process payments, and coordinate fulfillment and shipping with full auditability.

```
+-----------------------------------------------------------------------------+
|                               NexusFlow System                              |
+-----------------------------------------------------------------------------+

 Clients / ERP / Marketplace
              |
              v
   +--------------------+
   |   REST API (V1)    | <---> Swagger UI / Actuator
   +--------------------+
              |
              +-----------------------+-----------------------+
              |                       |                       |
              v                       v                       v
      +---------------+       +---------------+       +---------------+
      |  Customer     |       |  Product &    |       |    Order      |
      |  Management   |       |  Catalog      |       |  Orchestrator |
      +---------------+       +---------------+       +---------------+
              |                       |                       |
              +-----------------------+                       |
                                                              v
                                                      +---------------+
                                                      |   Inventory   |
                                                      |  Reservations |
                                                      +---------------+
                                                              |
                                           [Optimistic / Pessimistic Locking]
                                                              |
                                                              v
                                                  +-----------------------+
                                                  |  PostgreSQL 16 Engine |
                                                  |  (Flyway Migrations)  |
                                                  +-----------------------+
```

## Module Responsibilities
| Module | Responsibilities | Key Entities |
|---|---|---|
| **Customer** | Customer registration, status management, validation | `Customer` |
| **Product** | Product catalog, pricing, SKU management | `Product` |
| **Inventory** | Physical & reserved stock balance, concurrency control | `Inventory`, `InventoryReservation` |
| **Order** | Order lifecycle, aggregate calculation, reservation trigger | `Order`, `OrderItem` |
| **Shared** | Error handling (RFC 7807), OpenAPI config, base utilities | `ProblemDetail`, `GlobalExceptionHandler` |
