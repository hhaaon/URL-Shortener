# URL Shortener

## What It Does

This project is a backend URL shortener.

It allows a client to:

- create a short URL from a normal URL
- access the short URL and be redirected to the original URL

The system uses PostgreSQL for persistence, Redis for caching, and Redis-backed rate limiting to protect URL creation.

## Technology

- Java 21
- Maven
- Java `HttpServer`
- Jackson
- JDBC
- PostgreSQL
- Redis
- Jedis
- JUnit 5
- Mockito
- Testcontainers

## API

### POST /api/v1/urls

Creates a new short URL.

Example request:

```json
{
  "url": "https://example.com"
}
```

Success:

```text
201 Created
```

Example response:

```json
{
  "shortUrl": "http://localhost:8080/abc123"
}
```

Possible errors:

- `400 Bad Request` — malformed JSON, missing/null URL, or invalid URL
- `413 Payload Too Large` — request body exceeds 8 KiB
- `415 Unsupported Media Type` — request is not JSON
- `429 Too Many Requests` — rate limit exhausted
- `500 Internal Server Error` — unexpected backend failure
- `503 Service Unavailable` — rate limiter unavailable

### GET /{shortCode}

Redirects to the original URL.

Success:

```text
302 Found
Location: https://example.com
```

Possible error:

- `404 Not Found` — short code does not exist

## Architecture

The application is divided into several components with different responsibilities:

```text
Client
  |
  v
UrlShortenerHttpServer
  |
  +--> RateLimiter
  |      |
  |      v
  |    Redis
  |
  v
ShortUrlService
  |
  +--> ShortUrlCache ------> Redis
  |
  +--> ShortUrlRepository -> PostgreSQL
  |
  +--> ShortCodeGenerator
```

### Component Responsibilities

- `UrlShortenerHttpServer` handles HTTP requests and maps application results to HTTP responses.
- `ShortUrlService` contains the main application logic for creating and resolving short URLs.
- `ShortUrlRepository` provides persistence access to PostgreSQL.
- `ShortUrlCache` provides cached URL lookups through Redis.
- `ShortCodeGenerator` generates candidate short codes.
- `RateLimiter` protects the URL creation endpoint from excessive requests.

PostgreSQL is the source of truth for short URL mappings.

Redis is used as a performance layer for redirect lookups and as shared state for distributed rate limiting.

## Request Flows

### Create Short URL

```text
POST /api/v1/urls
↓
resolve client IP
↓
rate limiter
↓
check Content-Type
↓
parse JSON request
↓
validate original URL
↓
generate short code
↓
save mapping to PostgreSQL
↓
201 Created
```

If the generated short code already exists, the service generates another candidate and retries.

Rate limiting happens before request parsing and database writes so rejected traffic does not perform the more expensive state-changing work.

### Redirect

```text
GET /{shortCode}
↓
Redis cache lookup
├─ positive hit
│  → return cached URL
│
├─ negative hit
│  → return 404
│
└─ cache miss
   ↓
   PostgreSQL lookup
   ├─ found
   │  → populate Redis
   │  → redirect
   │
   └─ not found
      → store short-lived negative cache entry
      → return 404
```

A successful lookup returns:

```text
302 Found
Location: <original URL>
```

If Redis is unavailable during a redirect, the application falls back to PostgreSQL because PostgreSQL remains the authoritative source of the URL mapping.

## Redis Usage

Redis is used for two separate purposes in this project.

### 1. Redirect Cache

Redis caches short-code mappings to reduce PostgreSQL reads.

Positive cache example:

```text
short-url:abc123
→ https://example.com
```

Positive entries use a TTL.

Read behavior:

```text
cache hit
→ return cached URL
→ skip PostgreSQL

cache miss
→ query PostgreSQL
→ populate cache
→ return URL
```

The project also uses negative caching for missing short codes.

Example:

```text
short-url:ZZZZZZ
→ NOT_FOUND
```

Negative entries use a shorter TTL.

This prevents repeated requests for nonexistent codes from repeatedly querying PostgreSQL.

Redis is optional for redirect correctness. If Redis is unavailable, the application falls back to PostgreSQL.

### 2. Distributed Rate Limiting

Redis is also used to store shared token-bucket state for:

```text
POST /api/v1/urls
```

Each client is identified by its resolved client IP.

Example limiter key:

```text
rate-limit:create-url:203.0.113.50
```

The Redis hash stores:

```text
tokens
lastRefill
```

The limiter uses a token bucket with:

```text
capacity = 20
refill = 1 token every 3 seconds
```

The refill, availability check, token consumption, state update, and TTL refresh execute atomically inside Redis using a Lua script.

This is necessary because multiple application instances could otherwise race while updating the same bucket.

Inactive limiter buckets expire automatically using a Redis TTL.

## Failure Behavior

The application handles different failures according to the role of the failing component.

| Situation | Behavior |
|---|---|
| Malformed JSON or invalid URL | `400 Bad Request` |
| Non-JSON creation request | `415 Unsupported Media Type` |
| Unknown short code | `404 Not Found` |
| Short-code collision | Generate another code and retry |
| Non-collision database failure | Propagate the failure; do not retry it as a collision |
| Redirect cache miss | Query PostgreSQL |
| Redis cache unavailable during redirect | Fall back to PostgreSQL |
| Rate limit exhausted | `429 Too Many Requests` with `Retry-After` |
| Rate-limiter Redis unavailable | Fail closed with `503 Service Unavailable` |
| Oversized creation request | `413 Payload Too Large` |
| Unexpected creation backend failure | `500 Internal Server Error` |

### Why Redis Failures Behave Differently

For redirect caching:

```text
Redis
→ performance optimization

PostgreSQL
→ authoritative source of truth
```

Therefore, if Redis is unavailable, the redirect can still be completed correctly using PostgreSQL.

For URL-creation rate limiting:

```text
Redis
→ required to make the admission decision
```

If Redis is unavailable, the application cannot safely determine whether the request should be allowed.

The project therefore fails closed:

```text
rate limiter unavailable
→ 503 Service Unavailable
→ do not create the URL
```

This prevents a Redis outage from becoming an opportunity to bypass rate limiting and create URLs without abuse controls.

## Configuration

The application reads runtime configuration from environment variables.

### Database

| Variable | Default | Required |
|---|---|---|
| `DB_HOST` | `127.0.0.1` | No |
| `DB_PORT` | `5433` | No |
| `DB_NAME` | `url_shortener` | No |
| `DB_USER` | `postgres` | No |
| `DB_PASSWORD` | none | Yes |

`DB_PASSWORD` has no hard-coded default.

If it is missing or blank, the application fails fast during startup.

The project intentionally defaults to PostgreSQL port `5433`.

If your PostgreSQL installation uses the standard port `5432`, override the default before starting the application.

PowerShell example:

```powershell
$env:DB_PORT = "5432"
```

### Redis

| Variable | Default |
|---|---|
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |

### HTTP Server

| Variable | Default |
|---|---|
| `APP_PORT` | `8080` |
| `PUBLIC_BASE_URL` | `http://localhost:8080` |

`APP_PORT` is the internal port where the Java process listens.

`PUBLIC_BASE_URL` is the externally visible base URL returned to clients.

These values are intentionally separate because a production deployment may look like:

```text
Client
↓
https://sho.rt
↓
Reverse Proxy
↓
Java application on port 8080
```

### Trusted Proxy

Environment variable:

```text
TRUSTED_PROXY_IP
```

`TRUSTED_PROXY_IP` is blank by default.

Forwarded client-IP headers are trusted only when the immediate TCP peer matches the configured trusted proxy.

If no trusted proxy is configured, or if the immediate peer does not match the configured proxy, `X-Forwarded-For` is ignored and the TCP peer address is used.

This project supports one trusted reverse proxy.

When that proxy connects directly to the application, the first `X-Forwarded-For` value is used as the client identity for rate limiting.

If that first value is missing or blank, the application falls back to the TCP peer address.

The trusted proxy is expected to sanitize or overwrite client-supplied forwarding headers.

This application does not implement general multi-proxy trust-chain validation.

## Running Locally

### Requirements

- Java 21
- Maven
- PostgreSQL
- Redis

### PostgreSQL

Create a database named:

```text
url_shortener
```

Create the table:

```sql
CREATE TABLE short_urls (
    short_code VARCHAR(6) PRIMARY KEY,
    original_url TEXT NOT NULL
);
```

The application uses PostgreSQL port `5433` by default.

If your local PostgreSQL server runs on `5432`, configure:

```powershell
$env:DB_PORT = "5432"
```

Set the required database password:

```powershell
$env:DB_PASSWORD = "<your-local-password>"
```

Other database configuration values may use their defaults or be supplied through environment variables.

### Redis

Start Redis locally.

For example, using Docker:

```powershell
docker run `
    --name url-shortener-redis `
    -p 6379:6379 `
    -d redis
```

Verify Redis:

```powershell
docker exec url-shortener-redis redis-cli PING
```

Expected:

```text
PONG
```

### Start the Application

Run the application with Maven:

```powershell
mvn compile exec:java
```

By default, the HTTP server listens on:

```text
http://localhost:8080
```

### Example Request

PowerShell:

```powershell
Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/v1/urls" `
    -ContentType "application/json" `
    -Body '{"url":"https://example.com"}'
```

A successful request returns a generated short URL.

Opening the returned short URL causes the service to respond with an HTTP redirect to the original URL.

## Testing

The project uses JUnit 5, Mockito, and Testcontainers.

Requirements for the complete suite:

- Java 21
- Maven
- Docker

Run the complete automated test suite with:

```powershell
mvn test
```

Docker is required because the integration tests start disposable PostgreSQL and Redis containers.

### Integration Tests

The project includes integration tests against real PostgreSQL and Redis instances using Testcontainers.

Docker must be running before executing the complete test suite.

The integration tests cover:

- saving and loading short URLs through the real PostgreSQL JDBC repository
- translating PostgreSQL unique-key violations into `ShortCodeCollisionException`
- Redis positive-cache round trips
- Redis cache misses
- Redis negative-cache entries
- execution of the Redis Lua token-bucket script
- rate-limit capacity exhaustion
- independent rate-limit buckets for different clients

Testcontainers starts disposable PostgreSQL and Redis containers and exposes their dynamically mapped ports to the tests. The tests do not depend on PostgreSQL or Redis being installed directly on the developer machine.

Run only the infrastructure integration tests:

```powershell
mvn -Dtest=PostgresShortUrlRepositoryIntegrationTest,RedisShortUrlCacheIntegrationTest,RedisRateLimiterIntegrationTest test
```

Run the complete test suite:

```powershell
mvn test
```

The integration tests intentionally use real infrastructure because mock-based tests cannot prove that the SQL constraints, Redis commands, TTL behavior, or Lua rate-limiter script work correctly against the actual services.

Run only the HTTP-server tests with:

```powershell
mvn -Dtest=UrlShortenerHttpServerTest test
```

The focused tests cover important system behavior rather than only trivial getters or constructors.

Existing behavior includes tests for:

- cache hit skipping the repository
- cache miss falling back to the repository and populating Redis
- negative cache hit skipping PostgreSQL
- repository miss creating a negative-cache entry
- Redis cache outage falling back to PostgreSQL
- short-code collision generating another candidate
- non-collision database failure not being retried as a collision
- valid URL creation returning `201`
- invalid URL returning `400`
- exhausted rate limiter returning `429` with `Retry-After`
- unavailable rate limiter returning `503`

The trusted-proxy HTTP regression tests additionally verify that:

- a direct client cannot choose its rate-limit identity using `X-Forwarded-For`
- an untrusted TCP peer cannot supply a forwarded client identity
- the configured trusted proxy can supply the forwarded client identity
- a missing forwarded value falls back to the TCP peer
- only the first value of the supported `X-Forwarded-For` form is used
- a blank first forwarded value falls back to the TCP peer

## Design Decisions

### PostgreSQL Is the Source of Truth

Short URL mappings are persisted in PostgreSQL.

Redis can improve read performance, but losing cached data does not remove the authoritative mapping.

### Short Codes Are Random and Database-Enforced

Short codes are generated randomly using Base62 characters.

Random generation reduces collision frequency but cannot guarantee uniqueness.

The PostgreSQL primary key on `short_code` provides the authoritative uniqueness constraint.

If a collision occurs, the application generates another candidate and retries.

### Cache-Aside for Redirects

Redirect lookups first check Redis.

On a cache miss, PostgreSQL is queried and the result is added to Redis.

This reduces repeated database reads while keeping PostgreSQL authoritative.

### Negative Caching

Nonexistent short codes are cached temporarily.

This reduces repeated database queries caused by requests for invalid or nonexistent codes.

Negative entries use a shorter TTL because a code that does not exist now may become valid later.

### Distributed Token-Bucket Rate Limiting

URL creation is protected with a token bucket.

The limiter uses Redis so multiple application instances can share one limiter state.

The complete refill, check, consume, update, and expiry operation executes atomically in Redis using Lua.

This prevents race conditions between application instances while updating a bucket.

### Rate-Limiter Failure Policy

URL creation fails closed if the Redis-backed limiter is unavailable.

This endpoint creates persistent state, so preserving the abuse-control boundary is prioritized over write availability.

A limiter outage therefore results in:

```text
503 Service Unavailable
```

rather than allowing URL creation without the configured rate-limit decision.

### Trusted Proxy Design

Rate limiting needs a stable client identity, but HTTP forwarding headers can be supplied by clients and therefore cannot be trusted by default.

The application only accepts `X-Forwarded-For` when the immediate TCP peer matches the configured trusted proxy.

Direct clients and connections from any other peer are rate-limited using their TCP peer address instead.

This keeps the trust boundary explicit while deliberately supporting only a simple single-proxy deployment.

The configured reverse proxy is responsible for sanitizing or overwriting forwarding headers before sending requests to the application.

## Known Limitations

This project intentionally stops at a reasonable backend-portfolio scope rather than attempting to be a complete production URL-shortening platform.

Known limitations include:

- Uses Java's built-in `HttpServer` rather than Spring Boot.
- Uses `PGSimpleDataSource` rather than a production connection pool such as HikariCP.
- Has no authentication or user accounts.
- Supports only a simplified single trusted-proxy configuration.
- Does not validate a general chain of multiple reverse proxies.
- URL validation checks syntax and allowed schemes but does not verify that the destination is reachable.
- Rate-limit policy values are defined in the limiter implementation rather than fully externalized.
- No structured production logging.
- No metrics, distributed tracing, or health endpoints.
- No automated deployment or container orchestration.
- No production secret-management system.
- No custom short-code aliases.
- No URL expiration feature.
- No analytics or click tracking.
- No multi-region or high-availability architecture.

These limitations are documented rather than hidden because several belong to later infrastructure and production-engineering stages.