# FinFlow — Project Setup & Getting Started

A practical guide to clone, build, and run FinFlow **as of Phase 2 (Event Foundation complete)**.

For full system design, see [SystemArch.md](SystemArch.md).

---

## What is FinFlow?

FinFlow is a trading and payment processing platform built with:

- **Java 21** + **Quarkus 3.9.5**
- **Maven** multi-module layout
- **Lombok** + **MapStruct** (all modules)
- **PostgreSQL** (local) — event store + read models (Flyway)
- **Azure Event Hubs** (Kafka protocol) — messaging backbone for dev
- Planned: Redis, full REST APIs, React UI

**Current state:** Phase 2 complete. Postgres + Flyway run in order/payment/risk services. Kafka producer (`order-service`) and consumer (`matching-engine`) publish/consume `OrderPlaced` on Azure Event Hub `orders` via smoke test endpoint. Business REST APIs and matching logic are Phase 3.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|--------|
| Java | 21 | `java -version` |
| Git | any | optional |
| IDE | Cursor / IntelliJ | Enable annotation processing for Lombok + MapStruct |

| Tool | Phase | Notes |
|------|-------|-------|
| PostgreSQL | 2+ | Local install; databases `finflow_orders`, `finflow_payments`, `finflow_risk` |
| Azure Event Hubs | 2+ | Standard tier namespace + `orders` event hub (see [env.azure.example](env.azure.example)) |
| Redis | 3+ | Azure Cache for Redis or local Memurai |
| Node.js | 5+ | React frontend |

### Local configuration (before first run)

`application.properties` is gitignored. After cloning, copy the sample files:

```powershell
Copy-Item gateway\src\main\resources\application.properties.sample gateway\src\main\resources\application.properties
Copy-Item order-service\src\main\resources\application.properties.sample order-service\src\main\resources\application.properties
Copy-Item matching-engine\src\main\resources\application.properties.sample matching-engine\src\main\resources\application.properties
Copy-Item payment-service\src\main\resources\application.properties.sample payment-service\src\main\resources\application.properties
Copy-Item risk-engine\src\main\resources\application.properties.sample risk-engine\src\main\resources\application.properties
```

Edit each `application.properties` and set your PostgreSQL password (and JDBC URL if not using localhost).

### Azure Event Hubs credentials (Phase 2+)

Kafka clients authenticate via environment variables (never commit secrets):

1. Copy [env.azure.example](env.azure.example) to `.env.azure.local` (gitignored)
2. Paste your Event Hubs namespace connection string from Azure Portal
3. Load into PowerShell **before** starting services that use Kafka:

```powershell
Get-Content .env.azure.local | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
    Set-Item -Path "Env:$($matches[1].Trim())" -Value $matches[2].Trim()
  }
}
```

Required variables: `EVENTHUBS_BOOTSTRAP`, `EVENTHUBS_CONNECTION_STRING`.

---

## Project structure

```
finflow/
├── pom.xml                 # Parent POM, shared Lombok/MapStruct config
├── mvnw / mvnw.cmd         # Maven wrapper (use this, not system Maven)
├── common-lib/             # Shared Java library (all real code lives here today)
├── gateway/                # Quarkus service — port 8080 (scaffold)
├── order-service/          # Quarkus — port 8081 (Postgres, Kafka producer)
├── matching-engine/        # Quarkus — port 8082 (Kafka consumer)
├── payment-service/        # Quarkus service — port 8083 (scaffold)
├── risk-engine/            # Quarkus service — port 8084 (scaffold)
├── SystemArch.md           # Architecture & build phases
└── about_project.md        # Copy of architecture doc
```

### `common-lib` packages

| Package | Purpose |
|---------|---------|
| `com.finflow.common.domain` | `Money`, `Symbol`, `OrderId`, `OrderSide`, `OrderType` |
| `com.finflow.common.events` | Event records (`OrderPlaced`, `TradeExecuted`, `PaymentSettled`, …) |
| `com.finflow.common.dto` | Lombok DTOs for API/serialization |
| `com.finflow.common.mapper` | MapStruct mappers (event ↔ DTO), CDI-ready |
| `com.finflow.common.exceptions` | `FinFlowException` |

**Implemented today:** `order-service` (Flyway, `OrderPlacedPublisher`, smoke REST) and `matching-engine` (`OrderPlacedConsumer`). Other services are still scaffolds (health + Postgres migrations where applicable).

---

## Quick start

### 1. Clone / open the project

```powershell
cd F:\finflow
```

### 2. Build everything

```powershell
.\mvnw.cmd clean install
```

**Success:** all 7 modules build (`finflow-parent`, `common-lib`, and 5 Quarkus services).

**If `clean` fails** on a service (e.g. locked `gateway-dev.jar`), stop any running Quarkus dev process, then:

```powershell
.\mvnw.cmd install
```

### 3. Run a service in dev mode

From the repo root:

```powershell
# Gateway (port 8080)
.\mvnw.cmd quarkus:dev -pl gateway

# Order service (port 8081)
.\mvnw.cmd quarkus:dev -pl order-service

# Matching engine (port 8082)
.\mvnw.cmd quarkus:dev -pl matching-engine

# Payment service (port 8083)
.\mvnw.cmd quarkus:dev -pl payment-service

# Risk engine (port 8084)
.\mvnw.cmd quarkus:dev -pl risk-engine
```

### 4. Verify a service is up

| Service | Port | Health check |
|---------|------|----------------|
| gateway | 8080 | http://localhost:8080/q/health |
| order-service | 8081 | http://localhost:8081/q/health |
| matching-engine | 8082 | http://localhost:8082/q/health |
| payment-service | 8083 | http://localhost:8083/q/health |
| risk-engine | 8084 | http://localhost:8084/q/health |

Quarkus Dev UI (dev mode only): http://localhost:8080/q/dev/ (when gateway is running).

**Phase 2 smoke test** (requires Azure env vars loaded):

```powershell
# Terminal 1 — consumer
.\mvnw.cmd quarkus:dev -pl matching-engine

# Terminal 2 — producer
.\mvnw.cmd quarkus:dev -pl order-service

# Terminal 3 — publish test OrderPlaced
Invoke-RestMethod -Method POST -Uri http://localhost:8081/internal/smoke/orders
```

Expect `published` in the HTTP response, `Published OrderPlaced` in order-service logs, and `OrderPlaced smoke received` in matching-engine logs.

---

## IDE setup

### Lombok

- **IntelliJ:** Lombok plugin installed; enable annotation processing.
- **VS Code / Cursor:** Java extension + Lombok support (or run Maven build to verify).

### MapStruct

Generated implementations live under:

`common-lib/target/generated-sources/annotations/`

After changing a `@Mapper` interface, rebuild `common-lib`:

```powershell
.\mvnw.cmd compile -pl common-lib
```

### Injecting mappers in services (when you add code)

MapStruct mappers use `componentModel = "jakarta-cdi"`. In a Quarkus service:

```java
@Inject
OrderPlacedMapper orderPlacedMapper;
```

---

## Build phases (where we are)

| Phase | Status | What it adds |
|-------|--------|----------------|
| **1 — Scaffold** | Complete | Modules, `common-lib`, green `mvn install` |
| **2 — Event Foundation** | Complete | Postgres, Flyway, Azure Event Hubs, Kafka smoke test |
| **3 — Core Services** | Next | REST, matching, payments, risk |
| **4 — Gateway** | Pending | JWT, rate limits, WebSocket |
| **5 — Frontend** | Pending | React UI |

---

## Phase 2 — Event Foundation (complete)

### PostgreSQL (local)

One server, three databases:

```sql
CREATE DATABASE finflow_orders;    -- order-service
CREATE DATABASE finflow_payments;  -- payment-service
CREATE DATABASE finflow_risk;      -- risk-engine
```

Flyway migrations: `domain_events`, `orders_view`, `positions_view`.

### Messaging — Azure Event Hubs

Dev uses **Azure Event Hubs** (Kafka protocol), not a local broker — avoids Docker/WSL on Windows.

| Azure resource | Value (example) |
|----------------|-------------------|
| Namespace | `finflow-dev-eh` (Standard tier, Kafka enabled) |
| Event hub | `orders` (12 partitions) |
| Bootstrap | `finflow-dev-eh.servicebus.windows.net:9093` |

Code paths:

| Module | Class | Role |
|--------|-------|------|
| order-service | `OrderSmokeResource` | `POST /internal/smoke/orders` |
| order-service | `OrderPlacedPublisher` | Publishes JSON to topic `orders` |
| matching-engine | `OrderPlacedConsumer` | Consumes from topic `orders` |

### Phase 3 infrastructure (next)

- **Redis** — order book, idempotency, rate limits
- Event hubs `trades`, `payments` (create in Azure when needed)

See [SystemArch.md §11](SystemArch.md) for full local/cloud dev notes.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `quarkus-bom:3.9.0` not found | Parent uses **3.9.5** — pull latest `pom.xml` |
| Jandex index version error with Quarkus | `common-lib` uses Jandex **3.1.7** (not 3.2.x) |
| `mvn clean` fails on `target/*.jar` | Stop `quarkus:dev`, delete `target/`, run `install` |
| MapStruct mapper not found at runtime | Rebuild `common-lib`; ensure Jandex index in jar |
| PowerShell `&&` not supported | Use `;` or separate commands |
| Kafka JAAS / `Could not find KafkaClient` | Load `EVENTHUBS_CONNECTION_STRING` in the same terminal before `quarkus:dev` |
| Quarkus tries Docker Kafka Dev Services | Harmless if Azure env is set; optional: `quarkus.kafka.devservices.enabled=false` |
| `application.properties.sample` warning | Rebuild after pom excludes `*.sample` from classpath |

---

## Useful commands

```powershell
# Full build
.\mvnw.cmd clean install

# Build only shared library
.\mvnw.cmd install -pl common-lib

# Build one service and its dependencies
.\mvnw.cmd install -pl order-service -am

# Package without tests (if tests added later)
.\mvnw.cmd install -DskipTests
```

---

## Related docs

- [SystemArch.md](SystemArch.md) — architecture, schemas, Kafka topics, K8s, CI/CD
- [README.md](README.md) — generic Quarkus getting-started (mostly boilerplate)

---

## Next step

**Phase 3 — Core Services:** implement `POST /orders` in order-service (event store + Kafka publish), then matching-engine order book + Redis, payment settlement, and risk pre-trade checks.
