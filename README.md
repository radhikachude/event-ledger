# Event Ledger System

This repository implements the **Event Ledger** distributed system using **Java (Spring Boot 3.3.x)**. It consists of two independent microservices that process financial transaction events with high resiliency, trace propagation, out-of-order tolerance, and idempotency guarantees.

## 1. System Architecture

```
                          ┌──────────────────────┐
Browser / Client ──────→  │  Event Gateway API    │ (Port 8080)
                          │  (public-facing)      │
                          └──────┬───────────────┘
                                 │ REST (sync) via RestClient
                                 ▼ (Trace ID propagation: X-Trace-Id)
                          ┌──────────────────────┐
                          │  Account Service      │ (Port 8081)
                          │  (internal)           │
                          └──────────────────────┘
```

- **Event Gateway API (Public-Facing, Port 8080):** Accepts financial transaction events, enforces schema validation, verifies idempotency against a local H2 database, stores events, and routes transactions to the Account Service.
- **Account Service (Internal, Port 8081):** Manages account state and ledger balances, ensuring they remain correct regardless of transaction arrival order. Calculates balances as `sum(CREDIT) - sum(DEBIT)`.

---

## 2. Core Features & Architectural Decisions

### Resiliency Pattern: Circuit Breaker
We chose the **Circuit Breaker** pattern (implemented via `Resilience4j`) to protect the Gateway service from cascading failures if the Account Service slows down or crashes.
- If the Account Service is down, the Circuit Breaker trips and routes calls to fallback methods.
- `POST /events` immediately returns `503 Service Unavailable` instead of blocking threads or timing out.
- Local queries (`GET /events/{id}` and `GET /events?account=...`) continue working normally from the Gateway's local database.
- Balance queries through the Gateway return a clear error stating the Account Service is unreachable.

### Idempotency
- Incoming events are checked against the Gateway's H2 database. If the `eventId` is already present, the Gateway immediately returns the existing event with a `200 OK` status, preventing duplicate transactions and double-billing.
- The Account Service also validates transaction idempotency by tracking applied transaction event IDs.

### Chronological Out-of-Order Tolerance
- Since account net balance is computed strictly as the sum of CREDITs minus the sum of DEBITs, transaction calculation is mathematically commutative; out-of-order event arrivals do not affect final balances.
- Event list queries (`GET /events?account=...`) and account transactions are sorted chronologically by their original `eventTimestamp`.

### Distributed Tracing & Observability
- **Trace Propagation:** A trace ID (`X-Trace-Id`) is generated at the Gateway for incoming client requests, appended to MDC context for logging, and automatically propagated to the Account Service via Spring `RestClient` interceptors.
- **Structured JSON Logging:** Both services use Logstash encoder to output JSON logs.
- **Custom Metrics:** Microservices expose custom request and transaction metrics via Spring Boot Actuator `/actuator/metrics`.

---

## 3. Getting Started & Running

### Prerequisites
- **Java 17 or higher** installed.
- **Maven 3.8+** installed.
- **Docker & Docker Compose** installed (optional, for containerized run).

### Local Setup & Build
1. Build the parent project and all modules:
   ```bash
   mvn clean package
   ```

### Running Locally (Manual)
Run each service in a separate terminal:
1. **Start the Account Service:**
   ```bash
   java -jar account-service/target/account-service-1.0.0-SNAPSHOT.jar
   ```
2. **Start the Event Gateway API:**
   ```bash
   java -jar event-gateway/target/event-gateway-1.0.0-SNAPSHOT.jar
   ```

### Running with Docker Compose
To build and spin up the complete environment inside containers:
```bash
docker-compose up --build
```
The Gateway API will be available at `http://localhost:8080` and the Account Service at `http://localhost:8081`.

---

## 4. Running the Tests
To run unit and integration tests covering idempotency, validation, trace propagation, chronological ordering, and circuit breaker fallbacks:
```bash
mvn test
```

---

## 5. API Testing Examples

### Submit an Event (New Transaction)
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {
      "source": "mainframe-batch"
    }
  }'
```

### Retrieve Local Event Details
```bash
curl http://localhost:8080/events/evt-001
```

### List Events Chronologically for Account
```bash
curl http://localhost:8080/events?account=acct-123
```

### Check Account Balance (Gateway Proxy)
```bash
curl http://localhost:8080/accounts/acct-123/balance
```
