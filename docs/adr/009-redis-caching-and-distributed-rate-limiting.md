# ADR-009: Redis Caching and Distributed Rate Limiting

## Status
Accepted

## Context
High-traffic e-commerce and order orchestration platforms face two distinct scalability and security challenges:
1. **Database Read Load:** Frequent catalog lookups (e.g. `GET /api/v1/products/{sku}`) cause redundant SQL queries to PostgreSQL, consuming database connections and CPU cycles.
2. **API Abuse & Noisy Neighbors:** Unrestricted client requests can lead to Denial of Service (DoS), resource starvation, or brute-force attempts on checkout and authentication endpoints.

## Decision
We integrated **Redis 7** for two core architectural capabilities:

### 1. Second-Level Application Caching
- Configured `RedisCacheManager` with JSON serialization and customized Time-To-Live (TTL) durations:
  - `productCatalogCache`: 15 minutes TTL (read-heavy, low mutation frequency).
  - `inventoryCache`: 2 minutes TTL.
- Used Spring declarative `@Cacheable(value = "productCatalogCache", key = "#sku")` on read operations and `@CacheEvict` on mutations (`createProduct`, `updateProduct`, `deleteProduct`).

### 2. Distributed Rate Limiting (Token Bucket / Sliding Window)
- Implemented `RateLimiterService` and `RateLimitFilter` backed by Redis atomic increments (`INCR`) and key expirations.
- Quotas: 100 requests/minute per authenticated user (or per IP address for anonymous traffic).
- Standard HTTP 429 response using RFC 7807 `ProblemDetail` with rate limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After: 60`).
- Applied **Fail-Open Strategy**: if Redis encounters transient timeouts, requests are permitted rather than blocking legitimate users.

## Consequences
### Positive
- Sub-millisecond response times for product queries (bypassing PostgreSQL entirely on cache hits).
- Immediate protection against API spamming, web scrapers, and denial-of-service traffic.
- Zero distributed state held in application memory instances.

### Negative / Trade-offs
- Introduces Redis as an infrastructure runtime dependency.
- Requires cache invalidation discipline (`@CacheEvict`) on write paths.
