# Event Ledger System

A distributed microservices application for processing financial transaction events with **idempotency, resiliency, observability, trace propagation, and consumer-driven contract testing**.

---

# Overview

The solution consists of two independently deployable Spring Boot microservices.

## Event Gateway (Port 8080)

The Event Gateway is the public-facing API.

Responsibilities:

- Receive transaction events
- Validate requests
- Enforce idempotency
- Persist events
- Forward transactions to Account Service
- Proxy account balance requests
- Apply Retry and Circuit Breaker
- Propagate trace IDs
- Expose health and metrics endpoints

Runs locally at:

```
http://localhost:8080
```

---

## Account Service (Port 8081)

The Account Service is responsible for maintaining account balances.

Responsibilities:

- Apply CREDIT transactions
- Apply DEBIT transactions
- Prevent duplicate transaction application
- Maintain account balances
- Store transaction history
- Support account and balance queries

Runs locally at:

```
http://localhost:8081
```

When running with Docker Compose, Account Service is only accessible internally by Event Gateway.

---

# Technology Stack

| Category | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | H2 |
| Persistence | Spring Data JPA |
| Build Tool | Maven |
| Resiliency | Resilience4j |
| Metrics | Micrometer |
| Observability | Spring Boot Actuator |
| Logging | Structured JSON Logging |
| Trace Propagation | X-Trace-ID |
| Contract Testing | Pact JVM |
| Testing | JUnit 5, Mockito |
| Containerization | Docker / Docker Compose |

---

# Architecture

```text
                           External Client
                                  |
                 +----------------+----------------+
                 |                                 |
           POST /events              GET /accounts/{id}/balance
                 |                                 |
                 +----------------+----------------+
                                  |
                                  v
         +------------------------------------------------+
         |                 Event Gateway                  |
         |                     :8080                      |
         +------------------------------------------------+
         |  Request validation                            |
         |  Event idempotency                             |
         |  Event persistence                             |
         |  Account balance proxy                         |
         |  Retry and Circuit Breaker                     |
         |  Trace-ID propagation                          |
         |  Health and metrics                            |
         +------------------------------------------------+
                   |                          |
                   | POST transaction         | GET balance
                   |                          |
                   +-------------+------------+
                                 |
                                 v
         +------------------------------------------------+
         |                Account Service                 |
         |                     :8081                      |
         +------------------------------------------------+
         |  Apply CREDIT and DEBIT transactions           |
         |  Prevent duplicate transaction application     |
         |  Store transaction history                     |
         |  Maintain current account balance              |
         |  Support account and balance queries           |
         |  Health and metrics                            |
         +------------------------------------------------+
```

## Service Communication

The Event Gateway is the public entry point and communicates with Account Service over HTTP.

```text
POST /events
    |
    | Event Gateway validates and stores the event
    |
    v
POST /accounts/{accountId}/transactions
    |
    | Account Service applies the transaction
    |
    v
Account balance updated
```

Balance queries follow this flow:

```text
GET /accounts/{accountId}/balance
    |
    | Public request received by Event Gateway
    |
    v
GET /accounts/{accountId}/balance
    |
    | Internal request to Account Service
    |
    v
Balance response returned through Event Gateway
```

## Data Ownership

Each service owns a separate H2 database:

```text
Event Gateway   → jdbc:h2:mem:eventdb
Account Service → jdbc:h2:mem:accountdb
```

The services do not share database tables or in-process state. All cross-service communication occurs through HTTP API contracts.

## Network Access

When running locally:

```text
Event Gateway   → http://localhost:8080
Account Service → http://localhost:8081
```

When running with Docker Compose:

```text
External client → http://localhost:8080
Event Gateway   → http://account-service:8081
```

Only Event Gateway is published to the host in Docker Compose. Account Service remains accessible only to containers on the internal Compose network.

---

# Key Features

- Event validation
- Idempotent event processing
- Duplicate transaction protection
- BigDecimal balance calculations
- Retry and Circuit Breaker
- Logging | Structured JSON Logging (Logback)
- Trace ID propagation
- Health monitoring
- Custom Micrometer metrics
- Consumer-driven contract testing using Pact

---

# Running Locally

## Prerequisites

- Java 21
- Maven 3.8+

## Build

```bash
mvn clean package
```

## Start Account Service

```bash
mvn -pl account-service spring-boot:run
```

Runs on:

```
http://localhost:8081
```

## Start Event Gateway

```bash
mvn -pl event-gateway spring-boot:run
```

Runs on:

```
http://localhost:8080
```

Since both services use in-memory H2 databases, restarting a service clears its stored data.

---

# Docker

Dockerfiles are provided for both services together with a root-level Docker Compose file.

Build:

```bash
mvn clean package
```

Run:

```bash
docker compose up --build
```

Event Gateway resolves the Account Service using:

```properties
account-service.base-url=${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}
```

Local execution uses:

```
http://localhost:8081
```

Docker Compose overrides this with:

```
http://account-service:8081
```

where `account-service` is the Docker service name.

> **Note**
>
> Docker configuration is included in the project. Docker was not executed locally because Docker was not installed in the development environment. The application was fully verified using local Spring Boot execution, Maven, Postman, and cURL.
> 
> ---

# Health Endpoints

Health monitoring is implemented using **Spring Boot Actuator**.

## Event Gateway

```text
GET http://localhost:8080/health
```

## Account Service

```text
GET http://localhost:8081/health
```

Expected response:

```json
{
  "status": "UP"
}
```

These endpoints are also used for Docker health checks.

---

# Metrics

Micrometer provides application metrics through Spring Boot Actuator.

Custom Event Gateway metrics include:

- `eventledger.events.submitted`
- `eventledger.events.duplicate`
- `eventledger.events.failed`

View available metrics:

```text
GET http://localhost:8080/metrics
```

Retrieve an individual metric:

```text
GET http://localhost:8080/metrics/eventledger.events.submitted
```

---

# Structured Logging & Trace Propagation

Both services generate structured JSON logs.

Each request includes or generates a trace identifier using:

```http
X-Trace-ID
```

The Event Gateway propagates the same trace ID to Account Service, allowing a request to be correlated across both services.

Current implementation uses manual trace propagation through HTTP headers.

---

# Resiliency

The Event Gateway protects downstream Account Service calls using **Resilience4j**.

Implemented patterns:

- Retry
- Circuit Breaker
- Connection timeout
- Read timeout
- Graceful fallback handling

If Account Service becomes unavailable:

- Retry attempts are automatically performed.
- Circuit Breaker prevents repeated failures.
- The client receives **HTTP 503 Service Unavailable**.
- Existing Gateway data (events) remains accessible.

---

# Validation

Incoming events are validated before processing.

Validation rules include:

- Required fields
- CREDIT or DEBIT transaction types only
- Positive transaction amount
- Valid ISO-8601 timestamp

Invalid requests return:

```text
HTTP 400 Bad Request
```

---

# Idempotency

Each event is uniquely identified by its `eventId`.

Behavior:

| Request | Response |
|---------|----------|
| First submission | HTTP 201 Created |
| Duplicate submission | HTTP 200 OK |

Duplicate events:

- Return the original stored event
- Do not call Account Service again
- Do not change account balances

Account Service also validates duplicate transaction `eventId`s before applying transactions, providing a second layer of protection.

---

# API

## Event Gateway (Public API)

### Submit Event

```http
POST /events
```

Example:

```text
POST http://localhost:8080/events
```

---

### Retrieve Event

```http
GET /events/{eventId}
```

Example:

```text
GET http://localhost:8080/events/evt-001
```

---

### List Events

```http
GET /events?account={accountId}
```

Example:

```text
GET http://localhost:8080/events?account=acct-123
```

Events are returned ordered by `eventTimestamp`.

---

### Retrieve Account Balance (Gateway Proxy)

```http
GET /accounts/{accountId}/balance
```

Example:

```text
GET http://localhost:8080/accounts/acct-123/balance
```

Flow:

```text
Client
    ↓
Event Gateway
    ↓
AccountServiceClient
    ↓
Account Service
    ↓
Balance Response
```

If Account Service is unavailable:

```text
HTTP 503 Service Unavailable
```

---

## Account Service (Internal API)

These endpoints belong to the internal Account Service.

They may be called directly during local development but are normally accessed through Event Gateway.

### Apply Transaction

```http
POST /accounts/{accountId}/transactions
```

Example:

```text
POST http://localhost:8081/accounts/acct-123/transactions
```

---

### Retrieve Account

```http
GET /accounts/{accountId}
```

---

### Retrieve Balance

```http
GET /accounts/{accountId}/balance
```

Example:

```text
GET http://localhost:8081/accounts/acct-123/balance
```

---

# Testing

The project includes:

- Unit tests
- Controller tests
- HTTP client tests
- Pact consumer and provider verification
- Manual end-to-end verification using Postman and cURL

Run the full test suite:

```bash
mvn clean test
```

Run Event Gateway tests:

```bash
mvn -pl event-gateway test
```

Run Account Service tests:

```bash
mvn -pl account-service test
```

## Automated Test Coverage

### Event Gateway

Tests cover:

- Event validation
- Event persistence
- Duplicate event detection
- Idempotent processing
- Event retrieval
- Account event retrieval
- Gateway balance proxy endpoint
- Account Service client
- Trace propagation
- Retry and Circuit Breaker
- HTTP status codes
- Error handling

### Account Service

Tests cover:

- Credit transaction processing
- Debit transaction processing
- Account creation
- Balance calculation using BigDecimal
- Duplicate transaction detection
- Transaction persistence

---

## Manual Verification

The following scenarios were manually verified using Postman and cURL.

### Event Gateway

| Endpoint | Verification |
|----------|--------------|
| `POST /events` | Submit transaction |
| `POST /events` (duplicate) | Verify idempotency |
| `GET /events/{eventId}` | Retrieve event |
| `GET /events?account={accountId}` | Retrieve account events |
| `GET /accounts/{accountId}/balance` | Gateway balance proxy |

### Account Service

| Endpoint | Verification |
|----------|--------------|
| `POST /accounts/{accountId}/transactions` | Apply transaction directly |
| `GET /accounts/{accountId}` | Retrieve account |
| `GET /accounts/{accountId}/balance` | Retrieve balance |

Additional verification included:

- Health endpoints
- Custom Micrometer metrics
- Request validation
- Duplicate event handling
- Trace propagation
- Retry behavior
- Circuit Breaker behavior
- Graceful degradation (HTTP 503)
- Correct balance calculation

---

# Consumer-Driven Contract Testing

The project uses **Pact JVM** to verify the HTTP contract between the two microservices.

## Consumer

Event Gateway acts as the consumer.

Consumer tests execute the real `AccountServiceClient` against a Pact mock server to generate the contract.

The generated contract verifies:

- HTTP method
- Request path
- Required headers
- JSON request body
- Response status
- Response body

Flexible Pact matchers validate request formats while avoiding brittle hard-coded values.

## Provider

Account Service acts as the provider.

Provider verification executes the generated Pact contract against the Spring MVC `AccountController` using MockMvc.

This ensures that future API changes remain compatible with the consumer.

---

# Design Decisions

Key architectural decisions:

- Separate microservices with independent responsibilities
- Independent persistence for each service
- Event idempotency using `eventId`
- Secondary duplicate protection within Account Service
- Financial calculations using `BigDecimal`
- Gateway as the only public API
- Account Service remains an internal service responsible only for account state
- Internal Account Service communication through HTTP
- Retry and Circuit Breaker using Resilience4j
- Structured JSON logging
- Trace propagation using `X-Trace-ID`
- Health monitoring with Spring Boot Actuator
- Custom Micrometer metrics
- Consumer-driven contracts using Pact

---

# Future Improvements

Potential production enhancements include:

- PostgreSQL instead of H2
- Kafka with a transactional outbox
- Prometheus and Grafana dashboards
- OpenTelemetry and Jaeger tracing
- Pact Broker
- Testcontainers
- GitHub Actions CI/CD
- OAuth2/JWT authentication
- Kubernetes deployment

---

# Project Structure

```text
event-ledger
├── account-service
│   ├── src
│   ├── Dockerfile
│   └── pom.xml
│
├── event-gateway
│   ├── src
│   ├── Dockerfile
│   └── pom.xml
│
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

# Summary

This project demonstrates a resilient Spring Boot microservices architecture with:

- Event validation
- Idempotent transaction processing
- Accurate account balance management
- Gateway balance proxying
- Retry and Circuit Breaker
- Graceful degradation
- Structured JSON logging
- Trace propagation
- Health monitoring
- Custom Micrometer metrics
- Docker and Docker Compose support
- Consumer-driven contract testing with Pact
- Unit, controller, and contract testing