# Event Ledger System

A distributed microservices application for processing financial transaction events with **idempotency, resiliency, observability, distributed tracing, and consumer-driven contract testing**.

---

# Overview

The solution consists of two Spring Boot microservices.

## Event Gateway (Port 8080)

The Event Gateway is the public-facing API responsible for receiving transaction events from clients.

Responsibilities:

- Receive transaction events
- Validate incoming requests
- Enforce idempotency
- Persist events
- Forward transactions to the Account Service
- Handle downstream failures using Retry and Circuit Breaker
- Generate and propagate distributed trace IDs

---

## Account Service (Port 8081)

The Account Service is an internal microservice responsible for maintaining account balances.

Responsibilities:

- Apply credit transactions
- Apply debit transactions
- Store transaction history
- Maintain current account balances
- Support balance and account queries

---

# Technology Stack

| Category | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Persistence | Spring Data JPA |
| Database | H2 |
| Build Tool | Maven |
| Resiliency | Resilience4j |
| Observability | Spring Boot Actuator |
| Metrics | Micrometer |
| Distributed Tracing | OpenTelemetry |
| Contract Testing | Pact JVM |
| Unit Testing | JUnit 5 |
| Mocking | Mockito |
| Containerization | Docker / Docker Compose |

---

# Architecture

```text
                    Client
                       |
                 POST /events
                       |
         +---------------------------+
         |      Event Gateway        |
         +---------------------------+
            Validation
            Idempotency
            Event Persistence
            Retry
            Circuit Breaker
            Distributed Tracing
                       |
                       |
                       v
         +---------------------------+
         |      Account Service      |
         +---------------------------+
            Apply Transaction
            Store Transaction
            Update Balance
```

---

# Key Features

- Event validation
- Idempotent event processing
- Duplicate event detection
- Distributed tracing
- Retry and Circuit Breaker
- Graceful error handling
- Health monitoring
- Custom application metrics
- Consumer-driven contract testing

---

# Running the Application

## Prerequisites

- Java 21
- Maven 3.8+
- Docker (optional)

---

## Build

```bash
mvn clean package
```

---

## Start Account Service

```bash
cd account-service
mvn spring-boot:run
```

Runs on:

```
http://localhost:8081
```

---

## Start Event Gateway

```bash
cd event-gateway
mvn spring-boot:run
```

Runs on:

```
http://localhost:8080
```

---

# Docker

The project includes Dockerfiles for both services together with Docker Compose.

Build and start both services:

```bash
docker compose up --build
```

The Event Gateway resolves the Account Service through an externalized configuration property:

```properties
account-service.base-url=${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}
```

- When running locally, the default value (`http://localhost:8081`) is used.
- When running with Docker Compose, the `ACCOUNT_SERVICE_BASE_URL` environment variable overrides the default and points to `http://account-service:8081`.

---

# Health Endpoints

Health monitoring is implemented using **Spring Boot Actuator**.

Both services expose:

```
GET /health
```

These endpoints provide service health information and are suitable for Docker health checks and orchestration platforms.

---

# Metrics

Micrometer is used for application metrics.

Custom metrics include:

- eventledger.events.submitted
- eventledger.events.duplicate
- eventledger.events.failed

Metrics are available through Spring Boot Actuator.

---

# Distributed Tracing

Every incoming request receives a unique trace identifier.

The Event Gateway propagates the following header to downstream services:

```
X-Trace-ID
```

This enables request correlation across microservices and simplifies troubleshooting.

---

# Resiliency

The Event Gateway uses **Resilience4j** for fault tolerance.

Implemented patterns include:

- Retry
- Circuit Breaker

If the Account Service becomes unavailable:

- automatic retries are attempted
- the circuit breaker prevents repeated failures
- the client receives an HTTP 503 response
- RFC7807 Problem Details are returned to provide a consistent error response

---

# Validation

Incoming events are validated before processing.

Validation rules include:

- Required fields
- Supported transaction types (CREDIT / DEBIT)
- Positive transaction amount
- Valid ISO-8601 timestamp

Invalid requests return:

```
HTTP 400 Bad Request
```

---

# Idempotency

Each event is uniquely identified by its `eventId`.

Behavior:

- First submission → HTTP 201 Created
- Duplicate submission → HTTP 200 OK

Duplicate events are detected before calling the Account Service, ensuring the same transaction is never applied twice.

---

# Testing

The project includes automated unit tests, contract tests, and manual end-to-end verification to validate both business logic and service interactions.

## Unit Testing

Unit tests cover the following areas:

### Event Gateway

- Event validation
- Duplicate event detection
- Idempotent event processing
- Event retrieval
- HTTP client behavior
- Trace propagation
- Retry and Circuit Breaker behavior
- Error handling

### Account Service

- Credit transaction processing
- Debit transaction processing
- Account creation
- Balance calculation
- Idempotency
- Transaction persistence

Run all tests:

```bash
mvn clean test
```

---

# Consumer-Driven Contract Testing (Pact)

The project uses **Pact JVM** to verify the HTTP contract between the two microservices.

## Consumer

The **Event Gateway** is the consumer.

A Pact consumer test executes the real `AccountServiceClient` against a Pact mock server to generate the contract.

The consumer verifies:

- HTTP method
- Request path
- Required headers
- JSON request payload
- Expected HTTP status
- Expected response body

Flexible Pact matchers are used to validate request formats instead of hardcoded values.

Matchers are defined for:

- `eventId`
- `accountId`
- `amount`
- `currency`
- `transaction type`
- `eventTimestamp`

Using matchers keeps the contract resilient to valid data variations while still enforcing the API structure expected by the consumer.

---

## Provider

The **Account Service** is the provider.

The generated Pact contract is verified against the real Spring MVC `AccountController` using **MockMvc**.

The provider verification ensures the controller continues to satisfy the consumer contract without requiring the business logic tests to be duplicated.

This verifies:

- Request mapping
- HTTP status
- Request deserialization
- Response serialization
- Consumer/provider compatibility

---

## Why Pact?

Unit tests verify business logic within each service.

Pact verifies the API contract **between** services.

This helps prevent breaking changes such as:

- changing request field names
- changing response formats
- changing endpoint paths
- changing required headers
- changing HTTP status codes

without affecting the consumer unexpectedly.

---

# Manual Verification

The following scenarios were manually verified using Postman:

- Event submission
- Duplicate event submission
- Event retrieval
- Account balance retrieval
- Health endpoints
- Metrics endpoint
- Distributed tracing
- Retry behavior
- Circuit Breaker fallback
- HTTP 503 responses when Account Service is unavailable

---

# API

## Event Gateway

### Submit Event

```http
POST /events
```

### Get Event

```http
GET /events/{eventId}
```

### List Events

```http
GET /events?account={accountId}
```

---

## Account Service

### Apply Transaction

```http
POST /accounts/{accountId}/transactions
```

### Get Account

```http
GET /accounts/{accountId}
```

### Get Balance

```http
GET /accounts/{accountId}/balance
```

---

# Design Decisions

The following architectural decisions were made for this implementation:

- Separate microservices with independent responsibilities
- Separate persistence for each service
- Idempotent event processing using `eventId`
- Consumer-driven API contracts using Pact
- Retry and Circuit Breaker for downstream resilience
- Distributed tracing using `X-Trace-ID`
- Health monitoring with Spring Boot Actuator
- Custom Micrometer application metrics
- Internal Account Service accessed only through the Event Gateway

---

# Future Improvements

Potential enhancements include:

- PostgreSQL instead of H2
- Apache Kafka for asynchronous event processing
- Redis caching
- Prometheus and Grafana dashboards
- Jaeger distributed trace visualization
- Pact Broker for contract sharing across repositories
- Testcontainers for integration testing
- GitHub Actions CI/CD pipeline
- Authentication and Authorization (OAuth2/JWT)
- Deploy services on Kubernetes with horizontal scaling and health probes

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

This project demonstrates the implementation of a resilient microservices architecture using Spring Boot.

Key capabilities include:

- Event validation
- Idempotent processing
- Account balance management
- Resilience4j Retry and Circuit Breaker
- Distributed tracing with OpenTelemetry
- Health monitoring with Spring Boot Actuator
- Custom Micrometer metrics
- Docker support
- Consumer-driven contract testing with Pact
- Comprehensive unit and contract testing