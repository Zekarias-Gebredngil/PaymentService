# Nadi Payment Service: Comprehensive Architecture, Functionality, Security & Performance Documentation

> **Service**: `payment-service` (`nadi`)  
> **Runtime**: Java 21 (LTS), Spring Boot  
> **Persistence**: PostgreSQL 14+ (Flyway Managed)  
> **Event Streaming**: Apache Kafka (KRaft Mode)  
> **Observability**: Grafana + Grafana Loki + Kafka UI  
> **Architecture**: Hexagonal (Ports & Adapters) + Domain-Driven Design (DDD)

---

## Table of Contents
1. [Architectural Overview](#1-architectural-overview)
2. [Functional Capabilities](#2-functional-capabilities)
   - [2.1 Top-Up Service (C2B Mobile Money)](#21-top-up-service-c2b-mobile-money)
   - [2.2 Withdrawal & Payout Service (B2C Mobile Money)](#22-withdrawal--payout-service-b2c-mobile-money)
   - [2.3 Account-to-Account Transfers (Internal Ledger)](#23-account-to-account-transfers-internal-ledger)
   - [2.4 Webhook & Callback Engine](#24-webhook--callback-engine)
   - [2.5 Background Reconciliation Engine](#25-background-reconciliation-engine)
   - [2.6 Account & User Management](#26-account--user-management)
   - [2.7 Event-Driven Messaging (Kafka Bus)](#27-event-driven-messaging-kafka-bus)
3. [Security Architecture & Defensive Measures](#3-security-architecture--defensive-measures)
   - [3.1 Cryptographic Signatures (SHA256withRSA)](#31-cryptographic-signatures-sha256withrsa)
   - [3.2 Distributed Two-Phase Idempotency](#32-distributed-two-phase-idempotency)
   - [3.3 Authentication, Passwords & Stateless Sessions](#33-authentication-passwords--stateless-sessions)
   - [3.4 Monetary Value Object Invariants](#34-monetary-value-object-invariants)
   - [3.5 Strict Payment State Machine](#35-strict-payment-state-machine)
   - [3.6 Immutable Double-Entry Ledger](#36-immutable-double-entry-ledger)
4. [Performance Optimization Practices](#4-performance-optimization-practices)
   - [4.1 Non-Blocking Reactive HTTP Client](#41-non-blocking-reactive-http-client)
   - [4.2 High-Throughput Event Streaming](#42-high-throughput-event-streaming)
   - [4.3 Database Indexing & Optimistic Locking](#43-database-indexing--optimistic-locking)
   - [4.4 Asynchronous Ring-Buffer Logging](#44-asynchronous-ring-buffer-logging)
   - [4.5 Memory & GC Efficiency (Java 21 Records)](#45-memory--gc-efficiency-java-21-records)
5. [Operational Infrastructure & Ports](#5-operational-infrastructure--ports)

---

## 1. Architectural Overview

The service strictly adheres to **Hexagonal Architecture** (Ports and Adapters) coupled with **Domain-Driven Design (DDD)**. The domain layer is decoupled from third-party payment rails, databases, and message brokers.

```
                      ┌───────────────────────────────────────────────┐
                      │              INTERFACES LAYER                 │
                      │  REST Controllers  │  Provider Webhook Ingest  │
                      └───────────────────────┬───────────────────────┘
                                              │
                                              ▼
                      ┌───────────────────────────────────────────────┐
                      │              APPLICATION LAYER                │
                      │   Inbound Ports    │    Application Services  │
                      │   (Use Cases)      │  (TopUp, Withdraw, Trf)  │
                      └───────────────┬───────────────┬───────────────┘
                                      │               │
                     Inbound Calls    │               │ Outbound Ports
                                      ▼               ▼
          ┌───────────────────────────────┐   ┌───────────────────────────────┐
          │         DOMAIN LAYER          │   │     INFRASTRUCTURE LAYER      │
          │  - Payment Aggregate Root     │   │  - PostgreSQL Repositories    │
          │  - Money Value Object         │   │  - Telebirr WebClient Adapter │
          │  - Domain Events              │   │  - Kafka Payment Producer     │
          │  - Business Invariants        │   │  - Loki4j Logging Appender    │
          └───────────────────────────────┘   └───────────────────────────────┘
```

---

## 2. Functional Capabilities

### 2.1 Top-Up Service (C2B Mobile Money)
* **File Reference**: `com.payment.nadi.application.service.TopUpService`
* **Purpose**: Orchestrates Customer-to-Business (C2B) deposits into the platform wallet via telecom money rails (e.g. Telebirr).
* **Two-Phase Lifecycle**:
  1. Creates a payment aggregate in `PENDING` state with a globally unique business reference.
  2. Dispatches a signed checkout request to Telebirr API and transitions state to `INITIATED`, returning a direct checkout URL to the client.
* **Idempotency Protection**: Verifies whether an `Idempotency-Key` was submitted. If a duplicate in-flight or completed request is detected, the cached checkout session is returned immediately.
* **Event Dispatch**: Emits `PaymentInitiatedEvent` to both the local Spring event bus and Apache Kafka.

### 2.2 Withdrawal & Payout Service (B2C Mobile Money)
* **File Reference**: `com.payment.nadi.application.service.WithdrawService`
* **Purpose**: Orchestrates Business-to-Customer (B2C) cashouts/disbursements from user wallet accounts to telecom mobile numbers or external banks.
* **Pre-Flight Balance Check**: Validates that the customer's wallet has sufficient cleared funds before creating any payment record.
* **Balance Reservation**: Atomically locks and reserves customer funds to prevent double-spending while awaiting gateway completion.
* **Compensating Transactions**: If the gateway returns an error, the service automatically rolls back the balance reservation.

### 2.3 Account-to-Account Transfers (Internal Ledger)
* **File Reference**: `com.payment.nadi.application.service.TransferService`
* **Purpose**: Performs instant, real-time balance transfers between two customer accounts inside the system.
* **Single Atomic Transaction Boundary**: Uses `@Transactional` to guarantee that both the debit on the source account and credit on the target account commit together.
* **Self-Transfer Prevention**: Blocks transfers where source account equals destination account.
* **Double-Entry Booking**: Simultaneously generates balanced debit and credit entries in the financial ledger.
* **Event Dispatch**: Publishes `TransferCompletedEvent`.

### 2.4 Webhook & Callback Engine
* **File Reference**: `com.payment.nadi.application.service.PaymentCallbackService`
* **Purpose**: Ingests asynchronous status updates from external payment rails.
* **Cryptographic Verification**: Rejects any webhook missing a valid RSA signature.
* **Idempotent Webhook Processing**: Detects previously settled callbacks, updates audit logs, and returns `200 OK` without duplicate ledger crediting.
* **Transaction Finalization**: On confirmed receipt of funds, credits the user's wallet, records settled `Transaction` entities, and transitions payment status to `COMPLETED`.

### 2.5 Background Reconciliation Engine
* **File Reference**: `com.payment.nadi.application.service.PaymentReconciliationService`
* **Purpose**: Automated background worker that recovers in-doubt or orphaned transactions.
* **Scheduled Polling**: Runs on a cron schedule scanning for payments stuck in `INITIATED` status.
* **Active Status Verification**: Contacts Telebirr's order query API to retrieve authoritative gateway status.
* **Auto-Settlement / Expiry**: Automatically finalizes unacknowledged successful payments or marks abandoned payments older than 30 minutes as `EXPIRED`.

### 2.6 Account & User Management
* **File Reference**: `com.payment.nadi.interfaces.rest.AccountController`
* **Purpose**: User registration, authentication, and administrative controls.
* **Dynamic Onboarding**: Secure registration supporting role-based access control (`ROLE_CUSTOMER`, `ROLE_ADMIN`).
* **Admin Verification Gate**: Account approval mechanism for elevated permissions.
* **PIN & Password Management**: Self-service secure password modification and PIN resets.

### 2.7 Event-Driven Messaging (Kafka Bus)
* **File References**:
  - `com.payment.nadi.infrastructure.configuration.KafkaConfig`
  - `com.payment.nadi.infrastructure.messaging.KafkaPaymentProducer`
  - `com.payment.nadi.infrastructure.messaging.KafkaPaymentEventListener`
  - `com.payment.nadi.infrastructure.messaging.KafkaPaymentEventConsumer`
* **Dedicated Topics**:
  - `payment.completed` — Success lifecycle events (`PaymentCompletedEvent`)
  - `payment.failed` — Failure lifecycle events (`PaymentFailedEvent`)
  - `payment.initiated` — Checkout creation events (`PaymentInitiatedEvent`)
  - `payment.transfer-completed` — Internal transfer events (`TransferCompletedEvent`)
  - `payment.events` — Consolidated aggregate stream for data lake / auditing

---

## 3. Security Architecture & Defensive Measures

```
  Incoming Request
         │
         ▼
┌─────────────────────────────────┐
│   CorrelationIdFilter           │ ──► Distributed tracing & audit logging (MDC)
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   SecurityConfig (Spring Sec)   │ ──► Stateless, CORS strict origins, CSRF-disabled API
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   Idempotency Validator         │ ──► SHA-256 payload hash match & replay protection
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   TelebirrCryptoService         │ ──► SHA256withRSA signature verification (X.509)
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   Domain Model (Money, Payment) │ ──► Invariant validation, state machine transition checks
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   Double-Entry Ledger           │ ──► Immutable append-only audit trail
└─────────────────────────────────┘
```

### 3.1 Cryptographic Signatures (SHA256withRSA)
* **File Reference**: `com.payment.nadi.infrastructure.provider.telebirr.TelebirrCryptoService`
* **Algorithm**: `SHA256withRSA` using RSA 2048-bit key pairs.
* **Outbound Request Signing**: All outbound payloads are sorted into an ASCII canonical string (`TreeMap`) and signed using the merchant's **PKCS#8** Private Key.
* **Inbound Signature Verification**: All webhook notifications from payment gateways are verified against the provider's **X.509** Public Key. Requests with forged or altered parameters are rejected immediately.

### 3.2 Distributed Two-Phase Idempotency
* **File Reference**: `src/main/resources/db/migration/V1__initial_schema.sql` (`idempotency_records`)
* **SHA-256 Request Checksum**: Computes a SHA-256 fingerprint of the request payload to ensure that replayed keys with altered parameters are rejected as malicious conflicts.
* **Atomic Locking**: Uses an `IN_PROGRESS` database state to serialize concurrent requests with the same key.
* **Cached Responses**: Once completed, the full JSON response is stored and returned immediately on subsequent duplicate invocations without re-executing payment side effects.

### 3.3 Authentication, Passwords & Stateless Sessions
* **File Reference**: `com.payment.nadi.infrastructure.configuration.SecurityConfig`
* **BCrypt Hashing**: Passwords are hashed using BCrypt with randomized salt and an adaptive work factor.
* **Stateless Session Policy**: Configured with `SessionCreationPolicy.STATELESS` to eliminate session-hijacking and CSRF vulnerabilities.
* **Strict CORS Controls**: Restricts allowed origins, headers, and HTTP methods, exposing only necessary correlation headers (`X-Correlation-ID`, `Idempotency-Key`).

### 3.4 Monetary Value Object Invariants
* **File Reference**: `com.payment.nadi.domain.model.Money`
* **Immutability**: Represents money as an immutable Java record with `BigDecimal` and `Currency`.
* **Zero Negative Exposure**: Rejects negative amounts with `IllegalArgumentException`.
* **Currency Isolation**: Blocks arithmetic operations across mismatched currency codes.
* **Banker's Rounding**: Applies `RoundingMode.HALF_EVEN` up to the currency's official decimal fraction to prevent cumulative rounding discrepancies.

### 3.5 Strict Payment State Machine
* **File Reference**: `com.payment.nadi.domain.model.Payment`
* **Permitted Transitions**:
  $$\text{PENDING} \longrightarrow \text{INITIATED} \longrightarrow \text{COMPLETED} \mid \text{FAILED} \mid \text{EXPIRED}$$
* **Transition Enforcement**: Settled payments (`COMPLETED` or `FAILED`) are terminal. Any attempt to modify a terminal payment triggers an `IllegalStateException`.

### 3.6 Immutable Double-Entry Ledger
* **File Reference**: `src/main/resources/db/migration/V1__initial_schema.sql` (`ledger_entries`)
* **Append-Only Auditing**: Ledger records are immutable; no `UPDATE` or `DELETE` statements are ever executed against the ledger.
* **Financial Integrity**: Tracks both `DEBIT` and `CREDIT` entries along with calculated running balances for total balance reconciliation.

### 3.7 Zero-Trust Validation & Method Security
* **File References**: `com.payment.nadi.infrastructure.configuration.SecurityConfig`, `com.payment.nadi.interfaces.rest.AccountController`, `com.payment.nadi.interfaces.exception.GlobalExceptionHandler`
* **Bean Validation Enforcement**: Every inbound request DTO is annotated with `@Valid` enforcing strict constraints (`@NotBlank`, `@Size`, `@Email`, `@Positive`, etc.) prior to controller execution.
* **Method-Level Security**: `@EnableMethodSecurity` allows granular declarative authorization via `@PreAuthorize` across service and REST boundaries.
* **DTO Sanitization & Zero Entity Leakage**: Entity objects (`UserAccountEntity`, `PaymentEntity`) are completely abstracted behind immutable DTO records (`UserAccountDto`, `PaymentResponse`), preventing credential leakage or internal schema exposure.
* **Centralized Authorization Handling**: Denied operations generate standardized 403 Forbidden responses handled by `GlobalExceptionHandler`.

---

## 4. Performance Optimization Practices

### 4.1 Non-Blocking Reactive HTTP Client
* **File Reference**: `com.payment.nadi.infrastructure.configuration.WebClientConfig`
* **Reactor Netty Engine**: Replaces blocking `RestTemplate` with Netty-based non-blocking `WebClient`.
* **Granular Timeouts**: Configures connection (15s), read, and write timeouts to prevent thread starvation under slow upstream conditions.

### 4.2 Resilience4j Fault Tolerance Patterns
* **File Reference**: `com.payment.nadi.infrastructure.provider.telebirr.TelebirrClient`, `src/main/resources/application.yml`
* **Circuit Breaker**: Detects downstream provider outages using a sliding window of 10 calls. When failure rates exceed 50%, the circuit transitions to `OPEN` for 10 seconds, immediately short-circuiting calls and preventing resource starvation.
* **Exponential Backoff Retry**: Automatically attempts up to 3 retries with exponential backoff (multiplier 2x) for transient network and I/O failures.
* **Rate Limiting**: Throttles calls to payment rails (50 requests/sec) to avoid triggering upstream HTTP 429 penalties.
* **Graceful Fallbacks**: Degrades cleanly with standardized error responses if the circuit is open or downstream fails.

### 4.3 High-Throughput Event Streaming
* **File Reference**: `com.payment.nadi.infrastructure.messaging.KafkaPaymentProducer`
* **Asynchronous Publishing**: Uses `CompletableFuture` to dispatch events to Kafka asynchronously, ensuring REST response times are not held up by message broker I/O.
* **Partition Keying by `paymentId`**: Ensures all lifecycle events for the same payment land on the same partition in sequential order, while allowing parallel processing across multiple partitions for distinct payments.
* **Producer Idempotence**: `enable.idempotence=true` and `acks=all` eliminate duplicate writes and message loss.

### 4.4 Redis Distributed Caching Strategy
* **File Reference**: `com.payment.nadi.infrastructure.configuration.RedisConfig`
* **Explicit Cache TTLs**: Configures targeted eviction lifetimes using `RedisCacheManager`:
  - `payments` cache: 10 minutes TTL (for immutable terminal payments).
  - `account-lookups` cache: 5 minutes TTL (for recipient validation during transfers).
* **Null Value Caching Disabled**: Prevents cache pollution from missing entities.
* **JSON Serialization**: Employs `GenericJackson2JsonRedisSerializer` with `JavaTimeModule` for human-readable, schema-safe caching in Redis.
* **Event-Driven Cache Eviction**: Dynamic `@CacheEvict` updates cache on status transitions and account lifecycle modifications.

### 4.5 PostgreSQL HikariCP Connection Tuning & JDBC Batching
* **File Reference**: `src/main/resources/application.yml`
* **Connection Pool Optimization**:
  - `minimum-idle: 5`, `maximum-pool-size: 15`.
  - `leak-detection-threshold: 2000ms` detects any connection held open longer than 2 seconds.
  - `idle-timeout: 300000ms` (5m), `max-lifetime: 1200000ms` (20m).
* **JDBC Batching**: `reWriteBatchedInserts=true` rewrites individual insert statements into batched multi-row inserts at the JDBC driver layer.
* **Hibernate Batch Settings**: `batch_size: 25`, `order_inserts: true`, `order_updates: true`, and `batch_versioned_data: true`.
* **Read-Only Transactions**: Query services and wallet balance calculators explicitly declare `@Transactional(readOnly = true)` to avoid Hibernate dirty-checking and snapshot overhead.

### 4.6 Database Indexing & Optimistic Locking
* **File Reference**: `src/main/resources/db/migration/V1__initial_schema.sql`
* **High-Cardinality B-Tree Indexes**:
  - `idx_payments_reference` on `payments(reference)` — $O(1)$ payment lookups.
  - `idx_payments_customer_id` on `payments(customer_id)` — High-speed customer statement queries.
  - `idx_payments_status` on `payments(status)` — Efficient reconciliation batch scans.
  - `idx_idempotency_expires_at` on `idempotency_records(expires_at)` — Fast TTL expiration sweeps.
* **Optimistic Concurrency Control**: Uses `@Version` (`version BIGINT`) on the `payments` table to detect concurrent conflicting updates without blocking database table locks.

### 4.7 Asynchronous Ring-Buffer Logging
* **File Reference**: `src/main/resources/logback-spring.xml`
* **Loki4j Appender**: Dispatches structured logs to Grafana Loki via an asynchronous memory buffer.
* **Zero Request Latency Overhead**: HTTP log shipping runs on a dedicated background daemon thread, decoupling logging I/O from client request handling.

### 4.8 Memory & GC Efficiency (Java 21 Records)
* **Allocation Reduction**: DTOs, events, and value objects use Java records to minimize memory footprint and garbage collector overhead.
* **Thread-Local Tracing**: `CorrelationIdFilter` binds tracing IDs to the SLF4J MDC using lightweight thread-local storage.

---

## 5. Operational Infrastructure & Ports

The complete service stack runs in Docker Compose via `compose.yaml`:

| Service | Container Name | Port | Purpose | Access URL |
|---|---|---|---|---|
| **Payment Service** | `nadi-payment-service` | `8086` | Core REST API & Swagger UI | [http://localhost:8086/swagger-ui.html](http://localhost:8086/swagger-ui.html) |
| **Apache Kafka** | `nadi-kafka` | `9092` | KRaft Event Broker (PLAINTEXT) | `localhost:9092` |
| **Kafka Web UI** | `nadi-kafka-ui` | `8088` | Topic & Message Inspection UI | [http://localhost:8088](http://localhost:8088) |
| **Redis Cache** | `nadi-redis` | `6379` | Distributed Caching & Session Store | `localhost:6379` |
| **Redis Web UI** | `nadi-redis-commander` | `8085` | Key-Value & Cache Inspection UI | [http://localhost:8085](http://localhost:8085) |
| **Grafana** | `nadi-grafana` | `3000` | Log Analytics & Event Dashboards | [http://localhost:3000](http://localhost:3000) (User/Pass: `admin`/`admin`) |
| **Grafana Loki** | `nadi-loki` | `3100` | Centralized Log Ingestion Engine | [http://localhost:3100/ready](http://localhost:3100/ready) |
| **PostgreSQL** | `nadi-postgres-1` | `5432` | Relational Ledger & Payment Store | `localhost:5432` |


