# ADR-004: Stateless Authentication with JWT and Role-Based Access Control (RBAC)

## Status
Accepted

## Context
In a distributed order orchestration platform, stateful session management (e.g. HTTP sessions stored on a single server or sticky sessions behind a load balancer) introduces tight coupling and scalability bottlenecks. Distributed components and microservices need to independently verify the authenticity and permissions of callers without querying a centralized session store on every HTTP hop.

## Decision
We implemented **Stateless Authentication with JSON Web Tokens (JWT)** and **Spring Security 6**.

Key architectural features:
1. **Stateless Sessions:** `SessionCreationPolicy.STATELESS` ensures the server holds zero in-memory session state.
2. **Cryptographic Signing (HMAC-SHA256):** Tokens are digitally signed with a 256-bit secret key, enabling fast, local signature validation.
3. **Role-Based Access Control (RBAC):**
   - `ADMIN`: Full administrative access (create products, system management, actuator).
   - `WAREHOUSE_OPERATOR`: Stock replenishment and warehouse operations.
   - `FINANCE`: Billing, payment reconciliation, and ledger audit.
   - `CUSTOMER`: Order creation, viewing own order history.
4. **Declarative Method Security:** Enabled `@EnableMethodSecurity` to allow `@PreAuthorize("hasRole('ADMIN')")` at the service and controller levels.
5. **RFC 7807 Exception Translation:** Unauthorized (401) and Forbidden (403) errors return structured `ProblemDetail` JSON responses.

## Consequences
### Positive
- High scalability across horizontally scaled container instances.
- Standardized API consumption with Bearer tokens in Swagger UI and API clients.
- Fine-grained role segregation matching enterprise business functions.

### Negative / Trade-offs
- Tokens cannot be revoked before their expiration time without maintaining a token revocation denylist (e.g., in Redis, which will be implemented in Phase V0.6).
