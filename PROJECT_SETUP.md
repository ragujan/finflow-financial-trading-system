# FinFlow — Project Setup & Getting Started

A practical guide to clone, build, and run FinFlow **as of Phase 1 (Scaffold complete)**.

For full system design, see [SystemArch.md](SystemArch.md).

---

## What is FinFlow?

FinFlow is a trading and payment processing platform built with:

- **Java 21** + **Quarkus 3.9.5**
- **Maven** multi-module layout
- **Lombok** + **MapStruct** (all modules)
- Planned: PostgreSQL, Redpanda (Kafka), Redis, React UI

**Current state:** Shared library and empty service shells compile and package. No REST APIs, databases, or messaging yet.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|--------|
| Java | 21 | `java -version` |
| Git | any | optional |
| IDE | Cursor / IntelliJ | Enable annotation processing for Lombok + MapStruct |

**Not required yet for Phase 1:** PostgreSQL, Redpanda, Redis, Node.js (needed in Phase 2+).

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

---

## Project structure

```
finflow/
├── pom.xml                 # Parent POM, shared Lombok/MapStruct config
├── mvnw / mvnw.cmd         # Maven wrapper (use this, not system Maven)
├── common-lib/             # Shared Java library (all real code lives here today)
├── gateway/                # Quarkus service — port 8080 (scaffold)
├── order-service/          # Quarkus service — port 8081 (scaffold)
├── matching-engine/        # Quarkus service — port 8082 (scaffold)
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

Service modules currently contain only `pom.xml` and `application.properties` (HTTP port).

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

There are no custom REST endpoints yet — only Quarkus defaults and health.

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
| **2 — Event Foundation** | Next | Postgres, Flyway, Redpanda, event smoke test |
| **3 — Core Services** | Pending | REST, matching, payments, risk |
| **4 — Gateway** | Pending | JWT, rate limits, WebSocket |
| **5 — Frontend** | Pending | React UI |

---

## Phase 2 preview (not implemented yet)

When you start Phase 2, you will need:

### One PostgreSQL server, three databases

```sql
CREATE DATABASE finflow_orders;    -- order-service
CREATE DATABASE finflow_payments;  -- payment-service
CREATE DATABASE finflow_risk;      -- risk-engine
```

`gateway` and `matching-engine` do not use Postgres in this design (Redis/Kafka instead).

### Other infrastructure (later)

- **Redpanda** — Kafka-compatible broker (`orders`, `trades`, `payments` topics)
- **Redis** — order book, rate limits, sessions (Phase 3+)

See [SystemArch.md §11 — Local Development Setup](SystemArch.md) for native Windows install links.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `quarkus-bom:3.9.0` not found | Parent uses **3.9.5** — pull latest `pom.xml` |
| Jandex index version error with Quarkus | `common-lib` uses Jandex **3.1.7** (not 3.2.x) |
| `mvn clean` fails on `target/*.jar` | Stop `quarkus:dev`, delete `target/`, run `install` |
| MapStruct mapper not found at runtime | Rebuild `common-lib`; ensure Jandex index in jar |
| PowerShell `&&` not supported | Use `;` or separate commands |

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

Implement **Phase 2 — Event Foundation**: Flyway + Postgres in `order-service`, `payment-service`, and `risk-engine`, then Redpanda and a minimal producer/consumer test with `OrderPlaced`.
