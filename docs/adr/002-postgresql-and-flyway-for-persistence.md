# ADR-002: PostgreSQL and Flyway for Relational Persistence and Migration

## Status
Accepted

## Context
Enterprise order and inventory workflows require strict ACID guarantees, data consistency, relational constraints, foreign keys, and auditability. Furthermore, letting ORM frameworks like Hibernate automatically generate and alter database schemas in production environments leads to unexpected schema drifts, lock contention, and lack of reproducible deployments.

## Decision
We chose **PostgreSQL 16** as our primary relational database, coupled with **Flyway** for deterministic, version-controlled database migrations.

Key guidelines:
1. **Flyway Versioned Migrations:** All schema changes are defined in SQL scripts located in `src/main/resources/db/migration` (e.g., `V1__init_schema.sql`).
2. **JPA DDL Validation:** Hibernate is strictly configured with `spring.jpa.hibernate.ddl-auto: validate` to guarantee that code entities exactly match versioned database tables without dynamic alteration.
3. **Distributed Keys (UUID):** Primary keys use UUIDs to prevent ID enumeration, enable client/event generated identifiers, and simplify future sharding or database splitting.

## Consequences
### Positive
- Strict ACID transactions and isolation levels.
- Full traceability of schema evolutions in version control.
- Repeatable CI/CD deployments and rollback strategies.

### Negative / Trade-offs
- Manual migration writing is required for any model changes.
