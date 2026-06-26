# FinFlow — System Architecture & Build Guide

> A production-grade financial trading and payment processing platform built with Quarkus, Redpanda, PostgreSQL, Redis, React, Kubernetes, and GitHub Actions.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Project Structure](#4-project-structure)
5. [Microservices](#5-microservices)
6. [Event Sourcing & CQRS](#6-event-sourcing--cqrs)
7. [Kafka Topics & Partitioning](#7-kafka-topics--partitioning)
8. [Data Layer](#8-data-layer)
9. [API Gateway](#9-api-gateway)
10. [Frontend](#10-frontend)
11. [Local Development Setup (No Docker)](#11-local-development-setup-no-docker)
12. [Kubernetes & Helm](#12-kubernetes--helm)
13. [CI/CD Pipeline](#13-cicd-pipeline)
14. [Observability](#14-observability)
15. [Build Order](#15-build-order)

---

## 1. Project Overview

FinFlow is a simplified but architecturally complete trading and payment processing system. It demonstrates:

- **Event sourcing + CQRS** — every order, trade, and payment is an immutable event
- **Reactive microservices** — Quarkus with Mutiny for non-blocking I/O throughout
- **Concurrent order matching** — price-time priority order book backed by Redis
- **Distributed rate limiting** — token bucket per user/endpoint using Redis Lua scripts
- **Circuit breakers + bulkheads** — SmallRye Fault Tolerance protecting every downstream call
- **Real-time WebSocket feed** — live order book and trade updates pushed to the frontend
- **Full K8s deployment** — Helm charts, KEDA autoscaling on Kafka lag, rolling deploys
- **CI/CD pipeline** — GitHub Actions with staged promotion (dev → staging → prod)

---

## 2. System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                             │
│  React Dashboard    WebSocket Feed       REST / GraphQL         │
│  (Trading UI)       (Live prices)        (Batch queries)        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                       GATEWAY LAYER                             │
│         Quarkus API Gateway — port 8080                         │
│   JWT auth · Rate limiting · Circuit breaker · Load balancing   │
└──┬──────────────┬──────────────┬───────────────┬───────────────┘
   │              │              │               │
┌──▼───┐    ┌────▼────┐   ┌─────▼──────┐  ┌────▼──────┐
│Order │    │Matching │   │ Payment    │  │  Risk     │
│Svc   │    │Engine   │   │ Service    │  │  Engine   │
│:8081 │    │:8082    │   │ :8083      │  │  :8084    │
└──┬───┘    └────┬────┘   └─────┬──────┘  └────┬──────┘
   │              │              │               │
┌──▼──────────────▼──────────────▼───────────────▼───────────────┐
│                      EVENT BACKBONE                             │
│                    Redpanda (Kafka-compatible)                   │
│   orders · trades · payments · risk-alerts · audit-log          │
└──┬──────────────┬──────────────┬───────────────┬───────────────┘
   │              │              │               │
┌──▼───┐    ┌────▼──────┐  ┌────▼──────┐  ┌────▼──────────┐
│Trade │    │Read       │  │Notif.     │  │Audit Log      │
│Exec. │    │Projections│  │Service    │  │Consumer       │
└──┬───┘    └────┬──────┘  └────┬──────┘  └────┬──────────┘
   │              │              │               │
┌──▼──────────────▼──────────────▼───────────────▼───────────────┐
│                        DATA LAYER                               │
│   PostgreSQL 16      Redis 7         TimescaleDB (prod only)    │
│   Event store +      Order book ·    OHLCV tick                 │
│   Read models        Sessions ·      time-series                │
│                      Rate limits                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Stack

| Layer | Technology | Why |
|---|---|---|
| Microservices runtime | Quarkus 3.9 | Reactive by default (Mutiny), sub-10ms startup, native GraalVM support |
| Language | Java 21 | Virtual threads, records, pattern matching |
| Message broker | Redpanda | Kafka-API compatible, single binary, no JVM, no Zookeeper — 300 MB vs 4 GB |
| Primary database | PostgreSQL 16 | Event store + CQRS read models |
| Cache & state | Redis 7 | Order book, distributed rate limiting, session store |
| Time-series (prod) | TimescaleDB | OHLCV candles, tick data — plain partitioned PG table in dev |
| Frontend | React 18 + Vite + TypeScript | |
| State management | Zustand | Live WebSocket state (order book, positions) |
| Server state | React Query | REST data, caching, optimistic updates |
| Charts | Recharts | Candlestick OHLCV chart |
| Validation | React Hook Form + Zod | Order entry form |
| Container orchestration | Kubernetes | HPA, rolling deploys, pod disruption budgets |
| Autoscaling | KEDA | Scales matching engine on Kafka consumer lag |
| CI/CD | GitHub Actions | Build, test, image push, staged Helm deploy |
| Observability | Prometheus + Grafana + Jaeger | Metrics, dashboards, distributed tracing |
| Image build | Jib | Builds Docker images without Dockerfile, no Docker daemon needed |
| Secrets | Sealed Secrets | Encrypted secrets committed safely to Git |

---

## 4. Project Structure

### Backend — Maven Multi-Module

```
F:\finflow\
├── pom.xml                          ← parent pom, Quarkus BOM, module list
├── mvnw / mvnw.cmd                  ← Maven wrapper
├── common-lib/                      ← shared domain model, events, value objects
│   ├── pom.xml
│   └── src/main/java/com/finflow/common/
│       ├── domain/                  ← Money, Symbol, OrderId, OrderSide
│       ├── events/                  ← OrderEvent, TradeEvent, PaymentEvent (Avro)
│       └── exceptions/              ← base exception hierarchy
├── gateway/                         ← API gateway, auth, rate limiting
│   ├── pom.xml
│   └── src/main/java/com/finflow/gateway/
│       ├── filter/                  ← JWT validation, rate limit filter
│       ├── proxy/                   ← reactive HTTP proxy to each service
│       └── ws/                      ← WebSocket upgrade handler
├── order-service/
│   ├── pom.xml
│   └── src/main/java/com/finflow/order/
│       ├── domain/                  ← Order aggregate
│       ├── resource/                ← REST endpoints
│       ├── service/                 ← command handlers
│       └── kafka/                   ← event publishers
├── matching-engine/
│   ├── pom.xml
│   └── src/main/java/com/finflow/matching/
│       ├── orderbook/               ← OrderBook, PriceLevel, OrderQueue
│       ├── consumer/                ← Kafka consumer for OrderPlaced
│       └── redis/                   ← Redis order book persistence
├── payment-service/
│   ├── pom.xml
│   └── src/main/java/com/finflow/payment/
│       ├── ledger/                  ← double-entry ledger
│       ├── saga/                    ← settlement saga
│       └── idempotency/             ← Redis-backed idempotency keys
├── risk-engine/
│   ├── pom.xml
│   └── src/main/java/com/finflow/risk/
│       ├── checks/                  ← position limit, buying power checks
│       └── circuit/                 ← SmallRye circuit breaker config
└── finflow-ui/                      ← React frontend
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── features/
        │   ├── orderbook/
        │   ├── orders/
        │   ├── chart/
        │   ├── positions/
        │   └── auth/
        ├── hooks/
        │   ├── useWebSocket.ts
        │   └── useSymbol.ts
        ├── api/
        │   ├── client.ts            ← axios instance, JWT interceptor
        │   ├── orders.api.ts
        │   └── chart.api.ts
        └── store/
            └── wsDispatch.ts        ← routes WS messages to Zustand stores
```

### Parent `pom.xml` key sections

```xml
<groupId>com.finflow</groupId>
<artifactId>finflow-parent</artifactId>
<packaging>pom</packaging>

<modules>
  <module>common-lib</module>
  <module>gateway</module>
  <module>order-service</module>
  <module>matching-engine</module>
  <module>payment-service</module>
  <module>risk-engine</module>
</modules>

<properties>
  <quarkus.platform.version>3.9.0</quarkus.platform.version>
  <maven.compiler.release>21</maven.compiler.release>
</properties>

<dependencyManagement>
  <!-- Quarkus BOM manages all extension versions -->
  <dependency>
    <groupId>io.quarkus.platform</groupId>
    <artifactId>quarkus-bom</artifactId>
    <version>${quarkus.platform.version}</version>
    <type>pom</type>
    <scope>import</scope>
  </dependency>
</dependencyManagement>
```

---

## 5. Microservices

### 5.1 Gateway (port 8080)

The single entry point for all client traffic. Responsibilities:

**JWT Authentication**
- SmallRye JWT with RS256 key pair
- Roles: `TRADER`, `ADMIN`, `READ_ONLY`
- `@RolesAllowed` on every endpoint
- Token refresh stored in Redis (TTL = token expiry)

**Rate Limiting**
- Redis Lua script implementing token bucket per `userId:endpoint`
- Atomic — single round trip, no race conditions
- Returns `429 Too Many Requests` with `Retry-After` header
- Bucket sizes configurable per role (TRADER gets higher limits)

```lua
-- Token bucket Lua script (runs atomically in Redis)
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1]) or capacity
local last_refill = tonumber(bucket[2]) or now

local elapsed = now - last_refill
tokens = math.min(capacity, tokens + elapsed * refill_rate)

if tokens >= requested then
  tokens = tokens - requested
  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
  redis.call('EXPIRE', key, 3600)
  return 1
else
  return 0
end
```

**Circuit Breaker**
- `@CircuitBreaker` per downstream service
- Opens after 5 consecutive failures
- Half-open after 30 seconds
- `@Fallback` returns degraded response (cached last-known data)
- `@Bulkhead` isolates thread pools per service

**WebSocket Upgrade**
- Gateway upgrades `/ws` connections
- Bridges Kafka consumer → WebSocket sessions
- Clients subscribe by symbol: `{"action":"subscribe","symbol":"AAPL"}`
- Heartbeat ping every 30 seconds

### 5.2 Order Service (port 8081)

Handles order lifecycle — place, cancel, amend.

**REST Endpoints**
```
POST   /orders              Place a new order
GET    /orders              List orders (paginated)
GET    /orders/{id}         Get order by ID
PATCH  /orders/{id}/cancel  Cancel an open order
```

**Flow**
1. Validate request (Zod-equivalent Bean Validation)
2. Check risk engine pre-trade (synchronous HTTP call with circuit breaker)
3. Persist `OrderPlaced` event to event store (PostgreSQL)
4. Publish `OrderPlaced` to Kafka topic `orders`
5. Return `202 Accepted` with order ID

**Idempotency**
- Client generates UUID `orderId` and sends it in request
- Service checks Redis for duplicate within 24h
- Duplicate returns original response, no re-processing

### 5.3 Matching Engine (port 8082)

The crown jewel. Matches buy and sell orders using price-time priority.

**Order Book Data Structure**
```java
public class OrderBook {
    // Bids: highest price first
    private final TreeMap<BigDecimal, PriceLevel> bids =
        new TreeMap<>(Comparator.reverseOrder());

    // Asks: lowest price first
    private final TreeMap<BigDecimal, PriceLevel> asks =
        new TreeMap<>();
}

public class PriceLevel {
    private final BigDecimal price;
    // Time priority: FIFO queue at each price level
    private final Queue<Order> orders = new ConcurrentLinkedQueue<>();
    private volatile long totalQuantity;
}
```

**Matching Algorithm**
1. Consume `OrderPlaced` from Kafka
2. Load order book from Redis (ZSET per symbol)
3. Attempt match:
   - For BUY: find lowest ask ≤ buy price
   - For SELL: find highest bid ≥ sell price
4. If match found: publish `TradeExecuted` to Kafka `trades` topic
5. If partial fill: update remaining quantity in order book
6. Persist updated order book state to Redis

**Redis Order Book**
- Bids: `ZSET finflow:orderbook:{symbol}:bids` — score = price
- Asks: `ZSET finflow:orderbook:{symbol}:asks` — score = price
- Order details: `HASH finflow:order:{orderId}`

### 5.4 Payment Service (port 8083)

Handles settlement after a trade is executed.

**Double-Entry Ledger**
- Every settlement = debit one account + credit another
- Atomic PostgreSQL transaction — never partial
- `ledger_entries` table: `(id, account_id, amount, direction, trade_id, created_at)`

**Settlement Saga**
1. Consume `TradeExecuted` from Kafka
2. Debit seller's cash account
3. Credit buyer's cash account
4. Debit buyer's security position
5. Credit seller's security position
6. Publish `PaymentSettled` to Kafka `payments` topic

**Idempotency Keys**
- Redis key `finflow:idempotency:{tradeId}` with 24h TTL
- Check before processing — if exists, skip and return cached result
- Set after processing — prevents duplicate settlements on Kafka retry

### 5.5 Risk Engine (port 8084)

Pre-trade validation before any order is accepted.

**Checks performed (synchronous, called by Order Service)**
- Position limit: does this order exceed max position size for this symbol?
- Buying power: does the trader have sufficient cash/margin?
- Order size: is the order within min/max size limits?
- Symbol: is this symbol currently tradeable?

**Position Cache**
- Current positions cached in Redis, updated by payment service events
- Cache TTL = 5 seconds, refreshed from PostgreSQL on miss
- Reduces DB load during high-frequency trading

**Circuit Breaker on itself**
- If risk engine is overloaded, the circuit opens
- Gateway fallback: reject all new orders with `503 Service Unavailable`
- This is safer than allowing unvalidated orders through

---

## 6. Event Sourcing & CQRS

### Event Store Schema (PostgreSQL)

```sql
CREATE TABLE domain_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    sequence        BIGINT NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Optimistic locking: no two events with same aggregate+sequence
    UNIQUE (aggregate_id, sequence)
);

CREATE INDEX idx_events_aggregate ON domain_events (aggregate_id, sequence);
CREATE INDEX idx_events_type ON domain_events (event_type, created_at);
```

### Event Types

```java
// OrderEvent variants
public sealed interface OrderEvent permits
    OrderPlaced, OrderCancelled, OrderAmended, OrderFilled, OrderPartiallyFilled {}

public record OrderPlaced(
    UUID orderId,
    UUID traderId,
    String symbol,
    OrderSide side,        // BUY or SELL
    OrderType type,        // LIMIT or MARKET
    BigDecimal price,
    BigDecimal quantity,
    Instant placedAt
) implements OrderEvent {}

public record TradeExecuted(
    UUID tradeId,
    UUID buyOrderId,
    UUID sellOrderId,
    String symbol,
    BigDecimal price,
    BigDecimal quantity,
    Instant executedAt
) implements OrderEvent {}
```

### CQRS Read Projections

Write side (commands) → event store → Kafka → read side (projections)

```
Command: PlaceOrder
  → OrderService validates + persists OrderPlaced event
  → Publishes to Kafka topic 'orders'
    → Matching engine consumes → matches → publishes TradeExecuted
    → Read projection consumer upserts into orders_view table
    → Notification service consumes → pushes to WebSocket clients
```

**Read model tables** (optimised for queries, not normalisation):

```sql
-- Denormalised view for order blotter queries
CREATE TABLE orders_view (
    order_id        UUID PRIMARY KEY,
    trader_id       UUID NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    side            VARCHAR(4) NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- OPEN, FILLED, CANCELLED, PARTIAL
    price           NUMERIC(18,8),
    quantity        NUMERIC(18,8),
    filled_quantity NUMERIC(18,8) DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- Denormalised view for position queries
CREATE TABLE positions_view (
    trader_id       UUID NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    quantity        NUMERIC(18,8) NOT NULL,
    avg_cost        NUMERIC(18,8) NOT NULL,
    unrealised_pnl  NUMERIC(18,8),
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (trader_id, symbol)
);
```

---

## 7. Kafka Topics & Partitioning

| Topic | Partitions | Key | Retention | Notes |
|---|---|---|---|---|
| `orders` | 12 | `symbol` | 7 days | All order lifecycle events |
| `trades` | 12 | `tradeId` | 7 days | Executed trade events |
| `payments` | 6 | `tradeId` | 30 days | Settlement events |
| `risk-alerts` | 3 | `traderId` | 24 hours | Risk breach notifications |
| `audit-log` | 1 | `aggregateId` | 365 days | Compacted, immutable |

**Why partition by `symbol` for orders/trades?**
- All events for the same symbol go to the same partition
- The matching engine consumer maintains order book state per symbol
- Events for AAPL are always processed in order — no cross-symbol race conditions

**Consumer groups**
```
orders topic consumers:
  - matching-engine-group     (matching engine)
  - orders-projection-group   (CQRS read model)
  - audit-group               (audit log)

trades topic consumers:
  - payment-service-group     (settlement)
  - trades-projection-group   (CQRS read model)
  - notification-group        (WebSocket push)
  - audit-group               (audit log)
```

---

## 8. Data Layer

### PostgreSQL — Database Per Service

```
finflow_orders    ← order-service event store + orders_view
finflow_payments  ← payment-service ledger + payments_view
finflow_risk      ← positions_view, trader limits
```

Each service owns its database. No cross-service joins. Services communicate through Kafka events only.

### Redis Key Design

```
# Order book (sorted sets — score = price)
finflow:orderbook:{symbol}:bids       ZSET
finflow:orderbook:{symbol}:asks       ZSET

# Order details
finflow:order:{orderId}               HASH

# Rate limiting (token bucket state per user+endpoint)
finflow:ratelimit:{userId}:{endpoint} HASH  TTL=3600s

# Session store (JWT refresh tokens)
finflow:session:{userId}              STRING TTL=86400s

# Idempotency keys
finflow:idempotency:{tradeId}         STRING TTL=86400s

# Position cache (refreshed every 5s from DB)
finflow:position:{traderId}:{symbol}  STRING TTL=5s
```

### TimescaleDB (production only)

Used exclusively for OHLCV tick data — unsuitable for regular PostgreSQL because of the volume (millions of rows per day per symbol).

```sql
-- TimescaleDB hypertable (auto-partitions by time)
CREATE TABLE ohlcv (
    time        TIMESTAMPTZ NOT NULL,
    symbol      VARCHAR(20) NOT NULL,
    open        NUMERIC(18,8) NOT NULL,
    high        NUMERIC(18,8) NOT NULL,
    low         NUMERIC(18,8) NOT NULL,
    close       NUMERIC(18,8) NOT NULL,
    volume      NUMERIC(18,8) NOT NULL
);

SELECT create_hypertable('ohlcv', 'time');
CREATE INDEX ON ohlcv (symbol, time DESC);
```

**In local dev:** use a plain PostgreSQL partitioned table — identical queries, no extension needed.

---

## 9. API Gateway

### Quarkus Extensions Required

```xml
<dependencies>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-client-reactive</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-fault-tolerance</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-redis-client</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-websockets</artifactId>
  </dependency>
  <dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
  </dependency>
</dependencies>
```

### Service Ports

| Service | Dev Port | Health endpoint |
|---|---|---|
| Gateway | 8080 | /q/health |
| Order Service | 8081 | /q/health |
| Matching Engine | 8082 | /q/health |
| Payment Service | 8083 | /q/health |
| Risk Engine | 8084 | /q/health |
| React Dev Server | 5173 | — |
| Redpanda | 9092 | — |
| PostgreSQL | 5432 | — |
| Redis | 6379 | — |

### Gateway `application.properties` (dev profile)

```properties
# Dev profile — all %dev. prefixed properties override defaults in dev mode
%dev.quarkus.http.port=8080

# Downstream service URLs
%dev.order-service.url=http://localhost:8081
%dev.matching-engine.url=http://localhost:8082
%dev.payment-service.url=http://localhost:8083
%dev.risk-engine.url=http://localhost:8084

# JWT
mp.jwt.verify.publickey.location=META-INF/resources/publicKey.pem
mp.jwt.verify.issuer=https://finflow.dev

# Redis
%dev.quarkus.redis.hosts=redis://localhost:6379

# Kafka (Redpanda)
%dev.kafka.bootstrap.servers=localhost:9092

# Logging
%dev.quarkus.log.level=DEBUG
%dev.quarkus.log.category."com.finflow".level=DEBUG
```

---

## 10. Frontend

### State Architecture

The frontend has a strict three-layer state split:

**Zustand** — owns all WebSocket live state (updates 10–100x/sec)
- `useOrderBookStore` — bids[], asks[], spread, lastPrice
- `usePositionsStore` — positions[], totalPnL
- `useAuthStore` — token, user, isAuthenticated

**React Query** — owns all REST/server state
- `useOrders` — order history with pagination
- `useSubmitOrder` — mutation with optimistic update
- `useOhlcv` — candlestick data, staleTime 5s
- `useLogin` — auth mutation

**`wsDispatch.ts`** — single function routing all WebSocket messages to the right store

```typescript
// wsDispatch.ts — the critical glue between WebSocket and Zustand
export function dispatch(message: WSMessage) {
  switch (message.type) {
    case 'ORDER_BOOK_UPDATE':
      useOrderBookStore.getState().update(message.data);
      break;
    case 'TRADE_EXECUTED':
      useOrderBookStore.getState().addTrade(message.data);
      break;
    case 'POSITION_UPDATE':
      usePositionsStore.getState().update(message.data);
      break;
  }
}
```

### WebSocket Message Types

```typescript
type WSMessage =
  | { type: 'ORDER_BOOK_UPDATE'; data: OrderBookSnapshot }
  | { type: 'TRADE_EXECUTED';    data: Trade }
  | { type: 'POSITION_UPDATE';   data: Position }
  | { type: 'RISK_ALERT';        data: RiskAlert }
  | { type: 'HEARTBEAT';         data: { ts: number } }
```

### `useWebSocket` Hook

```typescript
// Key behaviours to implement:
// 1. Auto-reconnect with exponential backoff (1s, 2s, 4s, max 30s)
// 2. Heartbeat — send ping every 30s, close if no pong within 5s
// 3. Symbol subscription on connect
// 4. All messages dispatched through wsDispatch
// 5. Cleanup on unmount

const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws';
```

### Vite Config — Dev Proxy

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
});
```

---

## 11. Local Development Setup (No Docker)

### Why No Docker

Running Docker on 16 GB RAM with Kafka + Zookeeper + Schema Registry + Redis + PostgreSQL + TimescaleDB will consume ~10–12 GB before your application starts. Everything runs natively instead.

### RAM Budget

| Process | RAM Usage |
|---|---|
| Quarkus services × 5 (dev mode) | ~2.0 GB |
| PostgreSQL 16 native | ~1.2 GB |
| Redis 7 native | ~0.8 GB |
| Redpanda (Kafka replacement) | ~1.5 GB |
| React dev server (Vite) | ~0.7 GB |
| OS + IDE (VS Code / Cursor) | ~1.5 GB |
| **Total** | **~7.7 GB** |
| **Free** | **~8.3 GB** |

### Install Prerequisites (Windows)

```powershell
# 1. Java 21 — already done (confirmed working)

# 2. Redpanda (Kafka-compatible, single binary)
# Download from: https://github.com/redpanda-data/redpanda/releases
# Extract redpanda.exe to C:\redpanda\
# Start: C:\redpanda\redpanda.exe start --overprovisioned

# 3. PostgreSQL 16
# Download installer from: https://www.postgresql.org/download/windows/
# Install with default settings, remember the postgres password

# 4. Redis for Windows
# Download from: https://github.com/microsoftarchive/redis/releases
# Or use Memurai (Redis-compatible for Windows): https://www.memurai.com/

# 5. Node 20+
# Download from: https://nodejs.org/en/download/
```

### Start Everything

```powershell
# Terminal 1 — Redpanda
C:\redpanda\redpanda.exe start --overprovisioned

# Terminal 2 — Redis (or Memurai runs as Windows service automatically)
redis-server

# Terminal 3 — Gateway
cd F:\finflow
.\mvnw.cmd quarkus:dev -pl gateway -Dquarkus.http.port=8080

# Terminal 4 — Order Service
.\mvnw.cmd quarkus:dev -pl order-service -Dquarkus.http.port=8081

# Terminal 5 — Matching Engine
.\mvnw.cmd quarkus:dev -pl matching-engine -Dquarkus.http.port=8082

# Terminal 6 — Payment Service
.\mvnw.cmd quarkus:dev -pl payment-service -Dquarkus.http.port=8083

# Terminal 7 — Risk Engine
.\mvnw.cmd quarkus:dev -pl risk-engine -Dquarkus.http.port=8084

# Terminal 8 — React Frontend
cd F:\finflow\finflow-ui
npm run dev
```

### What to Skip in Dev vs What to Add in Prod

| Feature | Local Dev | Production (K8s) |
|---|---|---|
| Kafka broker | Redpanda binary | Redpanda or Confluent Cloud |
| Schema Registry | Skip — use JSON | Confluent Schema Registry |
| TimescaleDB | Plain PG partition | TimescaleDB extension |
| Redis | Single node | Redis cluster |
| Observability | Quarkus Dev UI only | Prometheus + Grafana + Jaeger |
| TLS | None | cert-manager on K8s |
| Secrets | `.env` files | Sealed Secrets operator |

### Quarkus Dev Mode Benefits

Quarkus dev mode gives you:
- **Hot reload** — change any Java file, save, reload in <1 second. No restart.
- **Dev UI** — open `http://localhost:8080/q/dev` to see config, beans, Kafka messages, health
- **Continuous testing** — tests run automatically in the background on file save

---

## 12. Kubernetes & Helm

### Helm Chart Structure

```
helm/
├── Chart.yaml                   ← umbrella chart
├── values.yaml                  ← default values
├── values-staging.yaml          ← staging overrides
├── values-prod.yaml             ← prod overrides
└── charts/
    ├── gateway/
    │   ├── Chart.yaml
    │   ├── templates/
    │   │   ├── deployment.yaml
    │   │   ├── service.yaml
    │   │   ├── hpa.yaml
    │   │   └── configmap.yaml
    ├── order-service/
    ├── matching-engine/
    ├── payment-service/
    ├── risk-engine/
    └── notification-service/
```

### Deployment Template Pattern

```yaml
# helm/charts/order-service/templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: {{ .Values.replicaCount }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0        # zero-downtime deploy
  template:
    spec:
      containers:
        - name: order-service
          image: ghcr.io/yourorg/finflow-order-service:{{ .Values.image.tag }}
          ports:
            - containerPort: 8081
          env:
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: {{ .Values.kafka.bootstrapServers }}
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: finflow-secrets
                  key: db-password
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /q/health/live
              port: 8081
          readinessProbe:
            httpGet:
              path: /q/health/ready
              port: 8081
```

### KEDA — Kafka Lag Autoscaling

The matching engine scales based on how far behind it is consuming orders — not CPU.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: matching-engine-scaler
spec:
  scaleTargetRef:
    name: matching-engine
  minReplicaCount: 2
  maxReplicaCount: 20
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: redpanda:9092
        consumerGroup: matching-engine-group
        topic: orders
        lagThreshold: "100"        # scale up when lag > 100 messages
```

### Network Policies

```yaml
# Deny all ingress by default
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
spec:
  podSelector: {}
  policyTypes:
    - Ingress
---
# Allow only gateway → services
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-gateway-to-services
spec:
  podSelector:
    matchLabels:
      tier: service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: gateway
```

### Pod Disruption Budget

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: matching-engine-pdb
spec:
  minAvailable: 1              # always keep at least 1 pod running during deploys
  selector:
    matchLabels:
      app: matching-engine
```

---

## 13. CI/CD Pipeline

### GitHub Actions Workflow

```
.github/workflows/
├── ci.yml          ← runs on every PR
└── cd.yml          ← runs on merge to main
```

### CI Pipeline (`ci.yml`)

Runs on every pull request:

```
1. Checkout code
2. Set up Java 21
3. Run: ./mvnw verify (unit tests + integration tests via Testcontainers)
4. SonarQube static analysis
5. Build Docker images with Jib (no Dockerfile, no Docker daemon)
6. Push to GitHub Container Registry (ghcr.io)
7. Tag image with commit SHA
```

```yaml
# .github/workflows/ci.yml (key steps)
- name: Build and push image
  run: |
    ./mvnw package -pl order-service \
      -Dquarkus.container-image.build=true \
      -Dquarkus.container-image.push=true \
      -Dquarkus.container-image.registry=ghcr.io \
      -Dquarkus.container-image.group=${{ github.repository_owner }} \
      -Dquarkus.container-image.tag=${{ github.sha }}
```

**Why Jib?** — Quarkus + Jib builds a Docker image from Maven without needing Docker installed or running. The image is pushed directly to the registry. Faster than `docker build` and reproducible.

### CD Pipeline (`cd.yml`)

```
On merge to main:
  1. Deploy to dev namespace (automatic)
     helm upgrade --install finflow ./helm \
       -f values-dev.yaml \
       --set image.tag=${COMMIT_SHA} \
       --atomic --timeout 5m

  2. Run smoke tests against dev

  3. Manual approval gate ──────────────────► Staging deploy
                                              helm upgrade --install ...
                                              --atomic (auto-rollback on failure)

  4. Manual approval gate ──────────────────► Production deploy
                                              helm upgrade --install ...
                                              --atomic
```

`--atomic` means if the rollout fails (pod crashes, health check fails), Helm automatically rolls back to the previous release. Zero manual intervention needed.

---

## 14. Observability

### Prometheus Metrics (Quarkus Micrometer)

Custom metrics to add in each service:

```java
// In matching engine
@Inject MeterRegistry registry;

// Track order throughput
Counter ordersProcessed = Counter.builder("finflow.orders.processed")
    .tag("symbol", symbol)
    .register(registry);

// Track match latency
Timer matchLatency = Timer.builder("finflow.match.latency")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry);
```

Key metrics to track:

| Metric | Service | Why |
|---|---|---|
| `finflow.orders.processed` | Order service | Throughput |
| `finflow.match.latency` p99 | Matching engine | Core SLA |
| `finflow.kafka.consumer.lag` | All consumers | Backpressure signal |
| `finflow.ratelimit.rejected` | Gateway | Security / abuse detection |
| `finflow.circuitbreaker.open` | Gateway | Downstream health |
| `finflow.payment.settled` | Payment service | Business metric |

### Distributed Tracing (OpenTelemetry + Jaeger)

Quarkus `quarkus-opentelemetry` extension auto-instruments:
- All REST calls (inbound and outbound)
- Kafka producer and consumer operations
- Database queries (Hibernate)

Trace propagation uses W3C TraceContext headers — a single user request generates a trace that spans gateway → order-service → risk-engine → Kafka → matching-engine → payment-service.

```properties
# application.properties — tracing config
quarkus.otel.exporter.otlp.endpoint=http://jaeger:4317
quarkus.otel.traces.sampler=parentbased_traceidratio
quarkus.otel.traces.sampler.arg=0.05   # 5% sampling in prod, 100% in dev
```

---

## 15. Build Order

Follow this sequence. Each phase builds on the previous one.

### Phase 1 — Scaffold (complete)
- [x] Maven multi-module `pom.xml`
- [x] Child module `pom.xml` files (common-lib, gateway, order-service, matching-engine, payment-service, risk-engine)
- [x] `common-lib` domain classes: `Money`, `Symbol`, `OrderId`, `OrderSide`, `OrderType`
- [x] `common-lib` event records: `OrderPlaced`, `TradeExecuted`, `PaymentSettled`
- [x] Verify `./mvnw clean install` passes for all modules

### Phase 2 — Event Foundation
- [x] PostgreSQL databases created (`finflow_orders`, `finflow_payments`, `finflow_risk`)
- [x] Flyway migrations for `domain_events` table in each DB
- [x] Flyway migrations for read model tables (`orders_view`, `positions_view`)
- [ ] Redpanda running locally, topics created
- [ ] Verify producer/consumer works end-to-end with a test event

### Phase 3 — Core Services
- [ ] Order service: REST endpoints + event publish
- [ ] Risk engine: pre-trade checks + Redis position cache
- [ ] Matching engine: order book data structure + Redis persistence
- [ ] Payment service: ledger + idempotency + settlement saga
- [ ] Integration test: place order → match → settle

### Phase 4 — Gateway
- [ ] JWT auth with RS256 key pair
- [ ] Rate limiting Lua script in Redis
- [ ] Circuit breakers on all downstream calls
- [ ] WebSocket endpoint + Kafka bridge

### Phase 5 — Frontend
- [ ] React + Vite + TypeScript project setup
- [ ] Zustand stores (orderbook, positions, auth)
- [ ] WebSocket hook with reconnect + heartbeat
- [ ] Order book component
- [ ] Order entry form with validation
- [ ] Candlestick chart

### Phase 6 — Kubernetes
- [ ] Helm umbrella chart + sub-charts per service
- [ ] KEDA ScaledObject for matching engine
- [ ] NetworkPolicy deny-all + allow rules
- [ ] PodDisruptionBudgets
- [ ] Sealed Secrets for DB passwords, JWT keys

### Phase 7 — CI/CD
- [ ] GitHub Actions CI: test + Jib build + push to GHCR
- [ ] GitHub Actions CD: dev auto-deploy + staging/prod manual gates
- [ ] Smoke test workflow against dev namespace

### Phase 8 — Observability
- [ ] Prometheus scrape config for all services
- [ ] Grafana dashboards (one per service + one overview)
- [ ] Jaeger tracing with sampling config
- [ ] Alerting rules: lag > 1000, latency p99 > 100ms, circuit breaker open

---

## Key Architectural Decisions

**Why Redpanda over Kafka locally?**
Kafka requires Kafka + Zookeeper + Schema Registry = 3 JVM processes, ~4 GB RAM. Redpanda is a single Rust binary, Kafka-API compatible, ~300 MB. Zero code changes needed.

**Why event sourcing?**
Financial systems are legally required to have an immutable audit trail. Event sourcing gives you this for free — every state change is an event. You can replay events to rebuild any read model, debug any historical state, and prove exactly what happened in any trade.

**Why CQRS?**
Order placement (write) and order book display (read) have completely different scaling characteristics. Writes need strong consistency. Reads need to be extremely fast and can tolerate slight staleness. CQRS lets you optimise each independently.

**Why Redis for the order book?**
The order book is read on every incoming order (potentially thousands per second). PostgreSQL round-trips would be too slow. Redis ZSET gives O(log N) insertion and O(1) top-of-book lookup with sub-millisecond latency.

**Why KEDA over plain HPA for the matching engine?**
CPU-based autoscaling is meaningless for the matching engine — it can be idle but falling behind because orders are accumulating in Kafka. KEDA scales on `consumer lag`, which is the actual signal that matters.

**Why Jib over Dockerfile?**
Jib builds container images from Maven without needing Docker installed or running. It produces layered images (dependencies in one layer, your code in another) so rebuilds after code changes are very fast — only the code layer changes, not the dependency layer.
