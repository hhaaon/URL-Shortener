# URL Shortener

## What It Does

This project is a backend URL shortener.

It allows a client to:

- create a short URL from a normal URL
- access the short URL and be redirected to the original URL

The system also uses PostgreSQL for persistence, Redis for caching,
and Redis-backed rate limiting to protect URL creation.

## Technology

- Java 21
- Maven
- Java HttpServer
- Jackson
- JDBC
- PostgreSQL
- Redis
- Jedis
- JUnit 5

## API

### POST /api/v1/urls

Creates a new short URL.

Example request:

{
"url": "https://example.com"
}

Success:

201 Created

Example response:

{
"shortUrl": "http://localhost:8080/abc123"
}

Possible errors:

400 Bad Request
429 Too Many Requests
503 Service Unavailable

### GET /{shortCode}

Redirects to the original URL.

Success:

302 Found
Location: https://example.com

Possible error:

404 Not Found

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

If the generated short code already exists, the service generates another code and retries.

Rate limiting happens before request parsing and database writes so rejected traffic does not perform the expensive state-changing work.

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

Redis is used for two separate purposes in this project:

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
| Invalid URL | `400 Bad Request` |
| Unknown short code | `404 Not Found` |
| Short-code collision | Generate another code and retry |
| Non-collision database failure | Propagate the failure; do not retry it as a collision |
| Redirect cache miss | Query PostgreSQL |
| Redis cache unavailable during redirect | Fall back to PostgreSQL |
| Rate limit exhausted | `429 Too Many Requests` with `Retry-After` |
| Rate-limiter Redis unavailable | Fail closed with `503 Service Unavailable` |

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

This prevents a Redis outage from becoming

## Configuration

The application reads runtime configuration from environment variables.

### Database

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
```

`DB_PASSWORD` is required and has no hard-coded default.

If it is missing, the application fails fast during startup.

### Redis

```text
REDIS_HOST
REDIS_PORT
```

### HTTP Server

```text
APP_PORT
PUBLIC_BASE_URL
```

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

```text
TRUSTED_PROXY_IP
```

Forwarded client-IP headers are trusted only when the immediate TCP peer matches the configured trusted proxy.

If no trusted proxy is configured, forwarded client-IP headers are ignored.

## Running Locally

### Requirements

- Java 21
- Maven
- PostgreSQL
- Redis

### PostgreSQL

Create a database:

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

Set the required database password environment variable before starting the application.

Example in PowerShell:

```powershell
$env:DB_PASSWORD = "<your-local-password>"
```

Other configuration values may use local defaults or be supplied through environment variables.

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

Run the application through the IDE or configured Java/Maven startup process.

By default, the HTTP server listens on:

```text
http://localhost:8080
```

## Testing

The project uses JUnit 5.

Run the automated test suite with:

```powershell
mvn test
```

The focused tests cover important system behavior rather than only trivial getters or constructors.

Examples include:

- cache hit skips the repository
- cache miss falls back to the repository and populates Redis
- negative cache hit skips PostgreSQL
- repository miss creates a negative-cache entry
- Redis cache outage falls back to PostgreSQL
- short-code collision causes another code to be generated
- non-collision database failure is not retried as a collision
- valid URL creation returns `201`
- invalid URL returns `400`
- exhausted rate limiter returns `429` with `Retry-After`
- unavailable rate limiter returns `503`

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

The limiter uses Redis so multiple application instances share one limiter state.

The complete refill/check/consume/update operation executes atomically in Redis using Lua.

This prevents race conditions between application instances.

### Rate-Limiter Failure Policy

URL creation fails closed if the Redis-backed limiter is unavailable.

This endpoint creates persistent state, so preserving the abuse-control boundary is prioritized over write availability.

A limiter outage therefore results in:

```text
503 Service Unavailable
```

rather than allowing unlimited creation traffic.

### Trusted Proxy Boundary

`X-Forwarded-For` is not trusted automatically.

It is considered only when the immediate connection came from a configured trusted proxy.

This prevents direct clients from selecting arbitrary rate-limit identities through spoofed HTTP headers.

## Known Limitations

This project intentionally stops at the current backend-fundamentals depth.

Known limitations include:

- Uses Java's built-in `HttpServer` rather than Spring Boot.
- Uses `PGSimpleDataSource` rather than a production connection pool such as HikariCP.
- Has no authentication or user accounts.
- Supports only a simplified trusted-proxy configuration.
- URL validation checks syntax and allowed schemes but does not verify that the destination is reachable.
- Rate-limit policy values are currently defined in the limiter implementation rather than fully externalized.
- No structured production logging.
- No metrics, distributed tracing, or health endpoints.
- No automated deployment or container orchestration.
- No production secret-management system.
- No custom short-code aliases.
- No URL expiration feature.
- No analytics or click tracking.
- No multi-region or high-availability architecture.

These limitations are documented rather than solved here because several belong to later stages of the backend roadmap.