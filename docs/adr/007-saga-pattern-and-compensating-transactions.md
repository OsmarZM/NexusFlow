# ADR-007: Saga Pattern and Compensating Transactions

## Status
Accepted

## Context
In distributed order management, a customer purchase spans multiple distinct steps:
1. Validating customer and creating order (`Order Domain`).
2. Locking inventory balance to prevent overselling (`Inventory Domain`).
3. Authorizing payment with external credit card / bank gateway (`Payment Domain`).
4. Generating fulfillment invoice and shipping label (`Shipping Domain`).

If payment fails or credit limit is exceeded at Step 3, the inventory locked at Step 2 cannot remain indefinitely reserved. We need an automated mechanism to rollback business state across modular boundaries without distributed transactions.

## Decision
We implemented the **Saga Pattern with Choreography & Compensating Transactions**.

Flow:
```
[Order Created] 
      │
      ▼
[Inventory Reserved]
      │
      ▼
[Payment Processing] 
      ├──> If SUCCESS: [Order Marked PAID] ──> [Physical Stock Deducted]
      │
      └──> If FAILED:  [SAGA COMPENSATION]
                             │
                             ├──> [Release Stock Reservation]
                             └──> [Cancel Order]
```

Key implementations:
- `OrderSagaOrchestrator` consumes `payments.processed` events.
- On payment decline, it invokes `OrderService.cancelOrder()`, which iterates over items and triggers `InventoryService.releaseReservation()`, returning reserved units back into the available pool.

## Consequences
### Positive
- High resilience and non-blocking asynchronous compensation.
- System automatically converges to a consistent final state.

### Negative / Trade-offs
- Intermediate states (e.g. `WAITING_PAYMENT`) are visible temporarily to queries (eventual consistency).
