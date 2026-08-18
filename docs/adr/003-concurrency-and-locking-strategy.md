# ADR-003: Concurrency Control and Stock Reservation Strategy

## Status
Accepted

## Context
In high-volume e-commerce and order fulfillment systems, concurrent checkout requests for high-demand items (e.g., flash sales, limited GPU stock) can cause race conditions leading to overselling (physical stock becoming negative). 

A basic read-then-write approach without database locks causes severe inconsistencies:
```
Stock = 1
Order A: Reads 1 ──────┐
                       ├──> Both reserve 1 ──> Stock becomes -1 (Overselling Bug!)
Order B: Reads 1 ──────┘
```

## Decision
We implemented a dual locking strategy tailored for stock management:
1. **Optimistic Locking (`@Version`):** The `Inventory` entity contains a `@Version private Long version;` field. Any conflicting write without lock is rejected with an `OptimisticLockingFailureException`.
2. **Pessimistic Write Locking (`SELECT ... FOR UPDATE`):** For critical checkout and stock reservation flows, `InventoryRepository.findBySkuWithPessimisticLock(sku)` acquires an exclusive row-level lock in PostgreSQL for the duration of the reservation transaction.
3. **Database Check Constraints:** Added database-level constraints `CONSTRAINT chk_inventory_available CHECK (physical_quantity >= reserved_quantity)` as a defensive safety barrier against data corruption.

## Consequences
### Positive
- Guaranteed zero overselling in high-concurrency environments.
- Total data consistency at both application and database engine layers.

### Negative / Trade-offs
- Pessimistic locking holds row locks for the duration of the transaction, which can increase latency under extreme contention on single SKUs. (Mitigated by short transaction scopes and read-through caching in future phases).
