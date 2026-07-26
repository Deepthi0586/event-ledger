# Event Ledger System

A distributed microservices system for processing financial transaction
events with idempotency, out-of-order tolerance, observability,
distributed tracing, and resiliency.

## Overview

The solution consists of two Spring Boot microservices:

-   **Event Gateway** (Port 8080)
   -   Receives transaction events
   -   Validates requests
   -   Enforces idempotency
   -   Persists events
   -   Calls Account Service
   -   Uses Resilience4j retry and circuit breaker
-   **Account Service** (Port 8081)
   -   Maintains account balances
   -   Stores transaction history
   -   Computes net balance from credits and debits

## Technology Stack

-   Java 21
-   Spring Boot 3
-   Spring Data JPA
-   H2 Database
-   Maven
-   Resilience4j
-   OpenTelemetry
-   Micrometer + Spring Boot Actuator
-   Docker / Docker Compose (preferred)

## Architecture

``` text
Client
   |
POST /events
   |
Event Gateway
   |-- Validation
   |-- Idempotency
   |-- Persistence
   |-- Retry / Circuit Breaker
   |
Account Service
   |
Account Balance + Transactions
```

## Running Locally

### Prerequisites

-   Java 21
-   Maven 3.8+

### Build

``` bash
mvn clean package
```

### Start Account Service

``` bash
cd account-service
mvn spring-boot:run
```

### Start Event Gateway

``` bash
cd event-gateway
mvn spring-boot:run
```

## Docker

Docker configuration is included.

``` bash
docker compose up --build
```

The Event Gateway uses the environment variable:

``` properties
account-service.base-url=${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}
```

allowing the same build to run locally or inside Docker.

## Health

Both services expose:

``` text
GET /health
```

through Spring Boot Actuator.

## Metrics

Custom Micrometer metrics:

-   eventledger.events.submitted
-   eventledger.events.duplicate
-   eventledger.events.failed

## Resiliency

Implemented with Resilience4j:

-   Retry
-   Circuit Breaker
-   Graceful 503 responses when Account Service is unavailable

## Distributed Tracing

A trace id is generated for each request and propagated to the Account
Service using:

``` text
X-Trace-ID
```

## Validation

Supported validation includes:

-   required fields
-   CREDIT / DEBIT only
-   positive amount
-   ISO-8601 timestamp

Invalid requests return HTTP 400.

## Idempotency

Submitting the same eventId:

-   First request → HTTP 201 Created
-   Duplicate request → HTTP 200 OK

No duplicate transaction is created.

## Testing

Automated tests cover:

-   Account Service business logic
-   Event Gateway business logic
-   HTTP client behavior
-   Trace propagation
-   Duplicate handling
-   Balance calculations
-   Validation
-   Retry and failure handling

Manual verification completed for:

-   end-to-end Gateway → Account Service flow
-   idempotency
-   health endpoints
-   metrics
-   resiliency (503)
-   account balance updates

Run tests:

``` bash
mvn clean test
```

## API

### Submit Event

``` http
POST /events
```

### Get Event

``` http
GET /events/{eventId}
```

### List Events

``` http
GET /events?account={accountId}
```

### Get Account

``` http
GET /accounts/{accountId}
```

### Get Balance

``` http
GET /accounts/{accountId}/balance
```

## Future Improvements

-   PostgreSQL
-   Kafka
-   Redis caching
-   Prometheus & Grafana
-   Jaeger visualization
-   Authentication / Authorization
-   Testcontainers
-   Contract testing
-   CI/CD pipeline

## Project Structure

``` text
event-ledger
├── account-service
├── event-gateway
├── docker-compose.yml
└── pom.xml
```