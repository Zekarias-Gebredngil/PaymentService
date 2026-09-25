# Payment Service Architecture

## 1. Overview

This document defines the comprehensive architecture for **Nadi Payment Service**, an enterprise-grade, high-throughput, horizontally scalable, and resilient payment orchestration and ledger microservice built on **Spring Boot 3.4+ / Java 21**.

The service operates as the core financial processing backbone within the Naditech ecosystem, providing unified APIs for:

* **Top-Up (C2B / Pay-In)**: Inbound funds collection from external mobile money providers.
* **Withdrawal (B2C / Payout)**: Outbound funds disbursement from customer balances to external mobile wallets.
* **Transfer (P2P / Account-to-Account)**: Internal atomic balance movement between accounts within the ledger.
* **Payment Status Tracking & Reconciliation**: Synchronous queries, asynchronous callbacks, and background polling workers.
* **Double-Entry Financial Ledger**: Immutable, auditable accounting system maintaining financial consistency across all transactions.
* **Provider Abstraction**: Decoupled provider adapter layer allowing seamless addition of new payment providers (Telebirr, Chapa, CBE Birr, Stripe) without altering core domain business rules.
* **Distributed Idempotency**: Strict two-phase idempotency mechanism preventing double charges and duplicate executions across all mutate endpoints.
* **Cryptographic Security & Verification**: Robust asymmetric RSA signing and signature verification for external gateway interactions.
* **Domain Event Streaming**: Publishing payment lifecycle events to Kafka/Event Bus for asynchronous downstream processing.

The initial external payment gateway integrated into Nadi is **Ethio Telecom's Telebirr** (supporting both C2B and B2C disbursement).

### Core Architectural Axiom

> **Telebirr is an integration adapter, not the payment architecture.**
> The domain model, transaction lifecycle, ledger rules, and public API remain strictly vendor-agnostic. All provider-specific communication protocols, cryptographic signing quirks, and data formats are quarantined within their respective infrastructure adapters.

---

## 2. Architectural Goals & Quality Attributes

The system adheres to standard enterprise architectural quality attributes:

### 2.1 Scalability
* **Stateless Application Tier**: Microservice nodes can scale horizontally behind an API Gateway or load balancer without session affinity.
* **Asynchronous Processing**: Long-running payment confirmations, external provider inquiries, and event publishing are offloaded to asynchronous workers and message brokers.
* **Database Optimization**: Read queries benefit from composite indexes, and write transactions are scoped tightly to minimize lock contention.

### 2.2 Financial Integrity & Reliability
* **Double-Entry Bookkeeping**: Every financial movement produces paired debit and credit entries whose sum equals zero ($\sum \text{Debits} + \sum \text{Credits} = 0$).
* **Double-Spend Prevention**: Combination of database-level pessimistic locking (`PESSIMISTIC_WRITE`) on account balances and optimistic locking (`@Version`) on payment aggregates.
* **At-Least-Once Delivery & Idempotency**: All payment requests require unique `Idempotency-Key` headers. Duplicate requests return cached responses without re-executing transfers or calling external gateways.
* **Circuit Breaking & Timeout Isolation**: Resilience4j circuit breakers, retries, and rate limiters insulate internal systems from external payment gateway slowdowns or downtimes.

### 2.3 Maintainability & Clean Separation
* **Hexagonal Architecture (Ports and Adapters)**: Core business rules are free of framework and third-party dependencies.
* **SOLID & DRY Principles**: High cohesion within use-case services and loose coupling via well-defined domain interfaces.
* **Zero Infrastructure Leaks**: Telebirr DTOs, HTTP headers, JPA entities, and database types never cross into the domain or application layers.

### 2.4 Security & Compliance
* **Zero-Credential Logging**: Automatic masking of sensitive data (phone numbers, account identifiers) and complete exclusion of private keys, secrets, and auth tokens from logs.
* **Cryptographic Request Signing**: Outgoing Telebirr requests are signed using RSA-SHA256 with the merchant private key; incoming callbacks are cryptographically verified using Telebirr's public key.
* **Correlation & Audit Trail**: Every request carries an `X-Correlation-ID` preserved across logs, MDC, database records, external provider calls, and emitted events.

---

## 3. High-Level System Architecture

```text
                           ┌────────────────────────────┐
                           │   Clients (Web / Mobile)   │
                           └─────────────┬──────────────┘
                                         │
                                         ▼
                           ┌────────────────────────────┐
                           │        API Gateway         │
                           │  - TLS Termination        │
                           │  - JWT/OAuth2 Auth         │
                           │  - Rate Limiting           │
                           │  - Request Correlation     │
                           └─────────────┬──────────────┘
                                         │
                                         ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                              NADI PAYMENT SERVICE                                 │
│                                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────────────┐  │
│  │                              Interface Layer                                │  │
│  │   TopUpController        WithdrawController        TransferController       │  │
│  │   PaymentController      TelebirrCallbackController GlobalExceptionHandler │  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
│                                         │                                         │
│  ┌──────────────────────────────────────▼──────────────────────────────────────┐  │
│  │                             Application Layer                               │  │
│  │   Inbound Ports:  TopUpUseCase, WithdrawUseCase, TransferUseCase,           │  │
│  │                   PaymentQueryUseCase, PaymentCallbackUseCase               │  │
│  │   Services:       TopUpService, WithdrawService, TransferService,           │  │
│  │                   PaymentQueryService, PaymentCallbackService               │  │
│  │   Outbound Ports: PaymentProvider, PaymentProviderResolver, WalletService,  │  │
│  │                   EventPublisher, IdempotencyStore                          │  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
│                                         │                                         │
│  ┌──────────────────────────────────────▼──────────────────────────────────────┐  │
│  │                                Domain Layer                                 │  │
│  │   Aggregates:     Payment, Transaction, LedgerEntry, PaymentAttempt         │  │
│  │   Value Objects:  Money, PaymentStatus, PaymentType, PaymentProviderType   │  │
│  │   Repositories:   PaymentRepository, TransactionRepository, LedgerRepo...   │  │
│  │   Domain Events:  PaymentInitiatedEvent, PaymentCompletedEvent, etc.       │  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
│                                         │                                         │
│  ┌──────────────────────────────────────▼──────────────────────────────────────┐  │
│  │                            Infrastructure Layer                             │  │
│  │  ┌──────────────────────┐ ┌──────────────────────┐ ┌─────────────────────┐ │  │
│  │  │   Provider Adapter   │ │  Persistence Adapter │ │  Messaging Adapter  │ │  │
│  │  │ TelebirrProvider     │ │ SpringData JPA Repos │ │ SpringEventPublisher│ │  │
│  │  │ TelebirrCryptoService│ │ Postgres Adapters    │ │ KafkaEventPublisher │ │  │
│  │  │ InternalLedgerProv.  │ │ IdempotencyStoreImpl │ │ (Future Outbox)     │ │  │
│  │  └──────────────────────┘ └──────────────────────┘ └─────────────────────┘ │  │
│  └─────────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────┬────────────────────────┬──────────────────────┬────────────┘
                       │                        │                      │
                       ▼                        ▼                      ▼
            ┌─────────────────────┐  ┌─────────────────────┐ ┌───────────────────┐
            │     PostgreSQL      │  │     Telebirr C2B    │ │   Kafka / Broker  │
            │  - payments         │  │     Telebirr B2C    │ │  - payment.events │
            │  - transactions     │  │  (Ethio Telecom API)│ │  - transfer.events│
            │  - ledger_entries   │  └─────────────────────┘ └─────────┬─────────┘
            │  - idempotency      │                                    │
            │  - payment_attempts │                       ┌────────────┼────────────┐
            └─────────────────────┘                       ▼            ▼            ▼
                                                    Notification    Wallet    Reporting
                                                      Service      Service     Service
```

---

## 4. Hexagonal Architecture (Ports and Adapters)

The codebase strictly adheres to Hexagonal Architecture (Ports & Adapters):

```text
               [Inbound Adapters]                 [Core Application]                [Outbound Adapters]
      
      HTTP Clients ───► TopUpController ─────► [TopUpUseCase]
                                                     │
      HTTP Clients ───► WithdrawController ──► [WithdrawUseCase]
                                                     │
      HTTP Clients ───► TransferController ──► [TransferUseCase]
                                                     │ 
      Telebirr Webhook ► TelebirrCallbackCtrl ►[PaymentCallbackUseCase]
                                                     │
                                                     ▼
                                            [Domain Business Logic]
                                            - Payment Aggregate
                                            - Money Value Object
                                            - Double-Entry Ledger
                                                     │
                                                     ├───────────────► [PaymentProvider] ──────► Telebirr Client
                                                     ├───────────────► [PaymentRepository] ────► PostgreSQL JPA
                                                     ├───────────────► [WalletService] ────────► DefaultLedgerWallet
                                                     ├───────────────► [IdempotencyStore] ─────► DB / Redis Store
                                                     └───────────────► [EventPublisher] ───────► Spring / Kafka Bus
```

### Dependency Rules:
1. **Domain Layer**: Has **zero** dependencies on Spring, JPA, HTTP, Jackson, or external libraries. It contains pure Java models, business invariants, and repository interfaces.
2. **Application Layer**: Depends only on the Domain Layer. It declares **Inbound Ports** (Use Cases) and **Outbound Ports** (Provider interfaces, persistence ports, messaging ports).
3. **Infrastructure Layer**: Implements outbound ports. It handles database persistence, HTTP WebClient calls to Telebirr, cryptographic key parsing, and Kafka event publishing.
4. **Interface Layer**: Implements inbound controllers, translates HTTP requests into Application DTOs, and formats responses into standard `ApiResponse<T>`.

---

## 5. Actual Project Structure

The project is packaged under `com.payment.nadi`:

```text
nadi/
├── pom.xml
├── compose.yaml
├── Architecture.md
└── src/
    ├── main/
    │   ├── java/com/payment/nadi/
    │   │   ├── NadiApplication.java
    │   │   │
    │   │   ├── domain/                                 <-- Pure Domain Logic (No framework deps)
    │   │   │   ├── model/
    │   │   │   │   ├── Payment.java                    # Payment Aggregate Root
    │   │   │   │   ├── Transaction.java                # Financial Transaction Entity
    │   │   │   │   ├── PaymentAttempt.java             # Gateway Interaction Audit Trail
    │   │   │   │   ├── LedgerEntry.java                # Immutable Double-Entry Ledger Record
    │   │   │   │   ├── Money.java                      # Value Object (BigDecimal + Currency)
    │   │   │   │   ├── PaymentType.java                # TOP_UP, WITHDRAW, TRANSFER, REFUND
    │   │   │   │   ├── PaymentStatus.java              # CREATED, PENDING, PROCESSING, SUCCESS, FAILED...
    │   │   │   │   └── PaymentProviderType.java        # TELEBIRR, INTERNAL_LEDGER, CHAPA, CBE_BIRR...
    │   │   │   ├── repository/
    │   │   │   │   ├── PaymentRepository.java          # Outbound Domain Port
    │   │   │   │   ├── TransactionRepository.java      # Outbound Domain Port
    │   │   │   │   ├── PaymentAttemptRepository.java   # Outbound Domain Port
    │   │   │   │   └── LedgerRepository.java           # Outbound Domain Port
    │   │   │   ├── event/
    │   │   │   │   ├── PaymentDomainEvent.java         # Base Domain Event
    │   │   │   │   ├── PaymentInitiatedEvent.java
    │   │   │   │   ├── PaymentCompletedEvent.java
    │   │   │   │   ├── PaymentFailedEvent.java
    │   │   │   │   └── TransferCompletedEvent.java
    │   │   │   └── exception/
    │   │   │       ├── PaymentException.java
    │   │   │       ├── InvalidPaymentException.java
    │   │   │       ├── InsufficientBalanceException.java
    │   │   │       ├── DuplicatePaymentException.java
    │   │   │       ├── PaymentNotFoundException.java
    │   │   │       ├── PaymentProviderException.java
    │   │   │       └── InvalidStateTransitionException.java
    │   │   │
    │   │   ├── application/                            <-- Application Orchestration & Use Cases
    │   │   │   ├── port/
    │   │   │   │   ├── in/                             # Inbound Ports (Invoked by Controllers)
    │   │   │   │   │   ├── TopUpUseCase.java
    │   │   │   │   │   ├── WithdrawUseCase.java
    │   │   │   │   │   ├── TransferUseCase.java
    │   │   │   │   │   ├── PaymentQueryUseCase.java
    │   │   │   │   │   └── PaymentCallbackUseCase.java
    │   │   │   │   └── out/                            # Outbound Ports (Implemented by Infra)
    │   │   │   │       ├── PaymentProvider.java
    │   │   │   │       ├── PaymentProviderResolver.java
    │   │   │   │       ├── WalletService.java
    │   │   │   │       ├── EventPublisher.java
    │   │   │   │       ├── IdempotencyStore.java
    │   │   │   │       └── dto/
    │   │   │   │           ├── PaymentProviderRequest.java
    │   │   │   │           ├── PaymentInitiationResult.java
    │   │   │   │           └── PaymentStatusResult.java
    │   │   │   ├── service/
    │   │   │   │   ├── TopUpService.java
    │   │   │   │   ├── WithdrawService.java
    │   │   │   │   ├── TransferService.java
    │   │   │   │   ├── PaymentQueryService.java
    │   │   │   │   ├── PaymentCallbackService.java
    │   │   │   │   └── PaymentReferenceGenerator.java
    │   │   │   └── dto/
    │   │   │       ├── request/
    │   │   │       │   ├── TopUpRequest.java
    │   │   │       │   ├── WithdrawRequest.java
    │   │   │       │   ├── TransferRequest.java
    │   │   │       │   └── PaymentMethodDto.java
    │   │   │       └── response/
    │   │   │           ├── PaymentResponse.java
    │   │   │           ├── TransactionResponse.java
    │   │   │           └── PageResponse.java
    │   │   │
    │   │   ├── infrastructure/                         <-- Technical Adapters & External Wiring
    │   │   │   ├── persistence/
    │   │   │   │   ├── entity/                         # JPA Database Entities
    │   │   │   │   │   ├── PaymentEntity.java
    │   │   │   │   │   ├── TransactionEntity.java
    │   │   │   │   │   ├── PaymentAttemptEntity.java
    │   │   │   │   │   ├── LedgerEntryEntity.java
    │   │   │   │   │   └── IdempotencyRecordEntity.java
    │   │   │   │   ├── repository/                     # Spring Data JPA Interfaces
    │   │   │   │   │   ├── SpringDataPaymentRepository.java
    │   │   │   │   │   ├── SpringDataTransactionRepository.java
    │   │   │   │   │   ├── SpringDataPaymentAttemptRepository.java
    │   │   │   │   │   ├── SpringDataLedgerEntryRepository.java
    │   │   │   │   │   └── SpringDataIdempotencyRepository.java
    │   │   │   │   ├── adapter/                        # Port Implementations
    │   │   │   │   │   ├── PaymentRepositoryAdapter.java
    │   │   │   │   │   ├── TransactionRepositoryAdapter.java
    │   │   │   │   │   ├── PaymentAttemptRepositoryAdapter.java
    │   │   │   │   │   ├── LedgerRepositoryAdapter.java
    │   │   │   │   │   └── DatabaseIdempotencyStore.java
    │   │   │   │   └── mapper/
    │   │   │   │       └── PaymentPersistenceMapper.java
    │   │   │   │
    │   │   │   ├── provider/
    │   │   │   │   ├── DefaultPaymentProviderResolver.java
    │   │   │   │   ├── InternalLedgerPaymentProvider.java
    │   │   │   │   └── telebirr/                       # Encapsulated Telebirr Adapter
    │   │   │   │       ├── TelebirrPaymentProvider.java
    │   │   │   │       ├── TelebirrClient.java
    │   │   │   │       ├── TelebirrCryptoService.java  # RSA-SHA256 Sign/Verify
    │   │   │   │       ├── TelebirrRequestMapper.java
    │   │   │   │       ├── TelebirrResponseMapper.java
    │   │   │   │       └── dto/
    │   │   │   │           ├── TelebirrC2BOrderRequest.java
    │   │   │   │           ├── TelebirrC2BOrderResponse.java
    │   │   │   │           ├── TelebirrB2CDisbursementRequest.java
    │   │   │   │           ├── TelebirrB2CDisbursementResponse.java
    │   │   │   │           ├── TelebirrQueryRequest.java
    │   │   │   │           ├── TelebirrQueryResponse.java
    │   │   │   │           └── TelebirrCallbackPayload.java
    │   │   │   │
    │   │   │   ├── wallet/
    │   │   │   │   └── DefaultWalletService.java       # Internal Ledger-backed Wallet Port
    │   │   │   ├── messaging/
    │   │   │   │   └── SpringEventPublisher.java       # Local Spring / Kafka event dispatch
    │   │   │   └── configuration/
    │   │   │       ├── PaymentProperties.java
    │   │   │       ├── SecurityConfig.java
    │   │   │       ├── WebClientConfig.java
    │   │   │       └── JacksonConfig.java
    │   │   │
    │   │   ├── interfaces/                             <-- Inbound HTTP REST Controllers
    │   │   │   ├── rest/
    │   │   │   │   ├── TopUpController.java
    │   │   │   │   ├── WithdrawController.java
    │   │   │   │   ├── TransferController.java
    │   │   │   │   ├── PaymentController.java
    │   │   │   │   └── TelebirrCallbackController.java
    │   │   │   └── exception/
    │   │   │       └── GlobalExceptionHandler.java
    │   │   │
    │   │   └── common/                                 <-- Shared Cross-Cutting Utilities
    │   │       ├── logging/
    │   │       │   └── CorrelationIdFilter.java        # MDC X-Correlation-ID Filter
    │   │       └── response/
    │   │           └── ApiResponse.java                # Standard Uniform JSON Envelope
    │   │
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/ (Flyway SQL scripts)
```

---

## 6. Ubiquitous Language & Core Domain Aggregates

### 6.1 Payment Aggregate Root
`Payment` represents the primary aggregate root managing the full lifecycle of a monetary intent:

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Immutable system primary identifier |
| `reference` | `String` | Human-readable tracking reference (e.g., `TOP-20260925-ABCD`, `WTH-20260925-1234`) |
| `type` | `PaymentType` | `TOP_UP`, `WITHDRAW`, `TRANSFER`, `REFUND` |
| `status` | `PaymentStatus` | `CREATED`, `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`, `CANCELLED`, `EXPIRED` |
| `amount` | `Money` | Value Object containing `BigDecimal` amount and `Currency` code |
| `customerId` | `UUID` | Customer initiating or owning the payment |
| `sourceAccountId` | `UUID` | Debited account ID (null for Top-Up from external cash/wallet) |
| `destinationAccountId`| `UUID` | Credited account ID (null for external withdrawal cash-out) |
| `provider` | `PaymentProviderType` | Gateway used (`TELEBIRR`, `INTERNAL_LEDGER`, etc.) |
| `providerReference` | `String` | Upstream gateway transaction ID / order number |
| `failureReason` | `String` | Sanitized reason for failure (if status is `FAILED`) |
| `createdAt` / `updatedAt` | `Instant` | UTC timestamps |

### 6.2 Transaction Entity
`Transaction` represents an executed financial booking associated with a finalized or in-progress payment:
* Represents the atomic financial event posted to accounts.
* Carries `fee`, `netAmount`, `transactionReference` (`TXN-xxxx`), and execution timestamps.

### 6.3 PaymentAttempt Entity
Preserves an immutable audit log of each interaction with external payment providers:
* Tracks `attemptNumber`, `providerReference`, request payloads, HTTP response codes, latency, and raw provider error strings.
* Ensures multi-attempt retries or query audits are visible without mutating the core `Payment` aggregate history.

### 6.4 LedgerEntry Entity
The immutable atomic line-item in the double-entry accounting ledger:
* Defines `accountId`, `transactionId`, `entryType` (`DEBIT` or `CREDIT`), `amount`, and `runningBalance`.

### 6.5 Money Value Object
```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        if (amount.scale() > 4) {
            throw new IllegalArgumentException("Monetary amount cannot exceed 4 decimal places");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Monetary amount cannot be negative");
        }
    }
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount.setScale(2, RoundingMode.HALF_EVEN), Currency.getInstance(currencyCode));
    }
}
```
* **No `float` or `double`**: Prevents binary floating-point rounding errors.
* **Scale & Rounding**: Defaults to 2 decimal places with `RoundingMode.HALF_EVEN` (Banker's rounding).
* **Currency Enforcement**: Strict validation using `java.util.Currency`.

---

## 7. Payment Status & State Transitions

### 7.1 State Machine Matrix

```text
               ┌───────────────┐
               │    CREATED    │
               └───────┬───────┘
                       │ (Initiate Provider Call)
                       ▼
               ┌───────────────┐
         ┌─────┤    PENDING    ├────────────────┐
         │     └───────┬───────┘                │
         │             │ (Callback / Processing)│
         │             ▼                        │
         │     ┌───────────────┐                │
         │     │  PROCESSING   │                │
         │     └───────┬───────┘                │
         │             │                        │
         ▼             ▼                        ▼
    ┌─────────┐   ┌─────────┐              ┌─────────┐
    │ FAILED  │   │ SUCCESS │              │ EXPIRED │
    └─────────┘   └────┬────┘              └─────────┘
                       │ (Optional)
                       ▼
                  ┌─────────┐
                  │ REFUNDED│
                  └─────────┘
```

### 7.2 Allowed Transitions

| Current Status | Target Status | Trigger |
|---|---|---|
| `CREATED` | `PENDING` | Payment submitted to provider; awaiting customer action or async webhook. |
| `CREATED` | `SUCCESS` | Internal ledger payment or synchronous provider immediate settlement. |
| `CREATED` | `FAILED` | Provider rejected order creation or network error during initiation. |
| `PENDING` | `PROCESSING` | Customer completed USSD push/web pin; gateway is clearing funds. |
| `PENDING` / `PROCESSING`| `SUCCESS` | Validated positive callback received or successful status query response. |
| `PENDING` / `PROCESSING`| `FAILED` | Negative callback (insufficient funds, user cancelled, PIN retry exceeded). |
| `PENDING` | `EXPIRED` | Payment timed out without callback within gateway validity window (e.g. 30 min). |
| `SUCCESS` | `REFUNDED` | Approved administrative or automated refund execution. |

**Invariants**:
* `SUCCESS`, `FAILED`, and `EXPIRED` are terminal states (except `SUCCESS -> REFUNDED`).
* A `SUCCESS` payment can **never** transition to `FAILED`.
* Any illegal transition attempt throws `InvalidStateTransitionException`.

---

## 8. Detailed End-to-End Operational Flows

### 8.1 Top-Up Flow (C2B / Pay-In)

```text
Client Application             Nadi API                  PaymentProvider              Telebirr Gateway
      │                           │                       (Telebirr Adapter)                  │
      │ 1. POST /topups           │                              │                            │
      │ (Idempotency-Key)         │                              │                            │
      ├──────────────────────────►│                              │                            │
      │                           │ 2. Check Idempotency Cache   │                            │
      │                           │ 3. Create Payment (CREATED)  │                            │
      │                           │ 4. Persist Payment           │                            │
      │                           │                              │                            │
      │                           │ 5. initiateTopUp(request)    │                            │
      │                           ├─────────────────────────────►│                            │
      │                           │                              │ 6. RSA Sign Request        │
      │                           │                              │ 7. POST /api/c2b/order     │
      │                           │                              ├───────────────────────────►│
      │                           │                              │                            │
      │                           │                              │ 8. Return Web/USSD URL     │
      │                           │                              │◄───────────────────────────┤
      │                           │ 9. Return Initiation Result  │                            │
      │                           │◄─────────────────────────────┤                            │
      │                           │                              │                            │
      │                           │ 10. Update Status: PENDING   │                            │
      │                           │ 11. Save PaymentAttempt      │                            │
      │ 12. Return 200 OK         │ 12. Publish InitiatedEvent   │                            │
      │ (checkoutUrl, ref)        │                              │                            │
      │◄──────────────────────────┤                              │                            │
      │                           │                              │                            │
      │ 13. Customer Enters PIN   │                              │                            │
      │     in Telebirr / USSD    │                              │                            │
      │.......................................................................................│
      │                                                          │                            │
      │                                      14. Async Callback  │                            │
      │                                      (Signed Payload)    │                            │
      │                           │◄──────────────────────────────────────────────────────────┤
      │                           │                              │                            │
      │                           │ 15. Verify RSA Signature     │                            │
      │                           │ 16. Verify Amount & Ref      │                            │
      │                           │ 17. WalletService.credit()   │                            │
      │                           │ 18. Update Status: SUCCESS   │                            │
      │                           │ 19. Record Transaction       │                            │
      │                           │ 20. Publish CompletedEvent   │                            │
      │                           │                              │                            │
      │                           │ 21. Return 200 {"code":0}    │                            │
      │                           ├──────────────────────────────────────────────────────────►│
```

### 8.2 Withdrawal Flow (B2C / Payout)

Withdrawal transfers funds from a customer's internal wallet out to an external Telebirr mobile wallet:

```text
Client Application             Nadi API                  WalletService                Telebirr Gateway
      │                           │                            │                              │
      │ 1. POST /withdrawals      │                            │                              │
      │ (Idempotency-Key)         │                            │                              │
      ├──────────────────────────►│                            │                              │
      │                           │ 2. Check Idempotency       │                              │
      │                           │ 3. Check Account & Balance │                              │
      │                           ├───────────────────────────►│                              │
      │                           │◄───────────────────────────┤                              │
      │                           │ 4. Create Payment(WITHDRAW)│                              │
      │                           │ 5. WalletService.debit()   │                              │
      │                           │    (Place Funds on Hold)   │                              │
      │                           ├───────────────────────────►│                              │
      │                           │                            │                              │
      │                           │ 6. Send B2C Disbursement   │                              │
      │                           │    (RSA-Signed Payload)    │                              │
      │                           ├──────────────────────────────────────────────────────────►│
      │                           │                            │                              │
      │                           │ 7. B2C Response            │                              │
      │                           │◄──────────────────────────────────────────────────────────┤
      │                           │                            │                              │
      │                           │ [If Gateway Success]:      │                              │
      │                           │   - Update Status: SUCCESS │                              │
      │                           │   - Record Transaction     │                              │
      │                           │   - Publish CompletedEvent │                              │
      │                           │                            │                              │
      │                           │ [If Gateway Rejection]:    │                              │
      │                           │   - Wallet.credit(Revert)  │                              │
      │                           ├───────────────────────────►│                              │
      │                           │   - Update Status: FAILED  │                              │
      │                           │   - Publish FailedEvent    │                              │
      │ 8. Return Response        │                            │                              │
      │◄──────────────────────────┤                            │                              │
```

### 8.3 Internal Transfer Flow (P2P / Ledger Movement)

```text
Client Application             TransferService               Ledger / WalletService
      │                               │                                 │
      │ 1. POST /transfers            │                                 │
      │ (sourceAcc, destAcc, amount)  │                                 │
      ├──────────────────────────────►│                                 │
      │                               │ 2. Validate Source != Dest      │
      │                               │ 3. Check Idempotency            │
      │                               │ 4. Acquire Lock on Accounts     │
      │                               ├────────────────────────────────►│ (Pessimistic Lock)
      │                               │ 5. Verify Sufficient Balance    │
      │                               │ 6. Create Payment Record        │
      │                               │ 7. Debit Source Account         │
      │                               │ 8. Credit Destination Account   │
      │                               │ 9. Post Balanced Ledger Entries │
      │                               │    (Sum Debits + Credits = 0)   │
      │                               │ 10. Record Transaction          │
      │                               │◄────────────────────────────────┤
      │                               │ 11. Mark Payment SUCCESS        │
      │                               │ 12. Publish TransferCompleted   │
      │ 13. Return 200 OK             │                                 │
      │◄──────────────────────────────┤                                 │
```

---

## 9. Telebirr Gateway Integration Deep Dive

### 9.1 Cryptographic Protocol
Telebirr requires asymmetric cryptography using **RSA-2048** and **SHA256withRSA**:

1. **Parameter Sorting (ASCII Natural Order)**:
   Every request map is sorted in lexicographical ASCII order using `java.util.TreeMap`.
   * Null values, empty strings, and the key `sign` itself are excluded.
   * Format: `key1=value1&key2=value2&key3=value3`.

2. **Digital Signature Generation**:
   * The formatted string is signed using the Merchant's RSA Private Key with algorithm `SHA256withRSA`.
   * The resulting signature bytes are Base64-encoded and attached as the `sign` field.

3. **Callback Signature Verification**:
   * When Telebirr sends a webhook callback to `/api/v1/payment-providers/telebirr/callback`, the payload contains payment details and a Base64-encoded `sign`.
   * Nadi reconstructs the ASCII sorted string from the callback parameters (excluding `sign`).
   * The signature is cryptographically verified against Telebirr's official Public Key using `Signature.getInstance("SHA256withRSA")`.
   * If the signature fails verification, the request is immediately rejected with HTTP 401/400 and logged as a security alert.

```text
  [Request Parameters] ──► [TreeMap ASCII Sort] ──► "amount=100&ref=TOP123..."
                                                               │
                                                 Merchant Private Key
                                                               │
                                                               ▼
                                                  [SHA256withRSA Signing]
                                                               │
                                                               ▼
                                                     [Base64 Encoded Sign]
```

### 9.2 Telebirr C2B Order Integration
* **API Endpoint**: `${TELEBIRR_BASE_URL}/api/c2b/order`
* **Request Structure (`TelebirrC2BOrderRequest`)**:
  * `appId`: Assigned merchant application ID.
  * `outTradeNo`: Nadi's unique `payment.reference`.
  * `totalAmount`: Payment amount formatted as string (e.g. `"100.00"`).
  * `shortCode`: Merchant short code.
  * `notifyUrl`: Public webhook callback URL.
  * `returnUrl`: Client redirection URL after browser checkout.
  * `timeoutExpress`: Expiration duration in minutes (e.g. `"30"`).
  * `sign`: RSA-SHA256 digital signature.

### 9.3 Telebirr B2C Disbursement Integration
* **API Endpoint**: `${TELEBIRR_BASE_URL}/api/b2c/disbursement`
* **Request Structure (`TelebirrB2CDisbursementRequest`)**:
  * `appId`, `shortCode`, `outTradeNo`, `amount`, `currency` (`ETB`).
  * `receiverPhone`: Customer's destination mobile number (`09...`).
  * `remark`: Disbursement description.
  * `sign`: RSA-SHA256 digital signature.

### 9.4 Telebirr Transaction Query API
* **API Endpoint**: `${TELEBIRR_BASE_URL}/api/c2b/query`
* Used by status inquiry endpoints and background reconciliation jobs to verify in-doubt payments.

---

## 10. Distributed Idempotency Mechanism

Financial systems must guarantee that no operation can ever be executed twice.

### 10.1 Two-Phase Idempotency Protocol

```text
Client                        IdempotencyStore                  Application Logic
  │                                  │                                  │
  │ POST /payments (Key: "k-123")    │                                  │
  ├─────────────────────────────────►│                                  │
  │                                  │                                  │
  │                     Phase 1: Check & Reserve Key                    │
  │                     ----------------------------                    │
  │                     Query key "k-123"                               │
  │                     ┌────────────────────────────────┐              │
  │                     │ Exists & Status == COMPLETED?  │──► Return Cached Response
  │                     │ Exists & Status == IN_PROGRESS?│──► Return 409 Conflict
  │                     │ Not Found?                     │              │
  │                     └────────────────────────────────┘              │
  │                     Reserve key "k-123" (IN_PROGRESS)               │
  │                                  │                                  │
  │                     Phase 2: Execution & Settlement                 │
  │                     -------------------------------                 │
  │                                  │  Execute Business Logic          │
  │                                  ├─────────────────────────────────►│
  │                                  │  Commit DB Transaction           │
  │                                  │◄─────────────────────────────────┤
  │                                  │                                  │
  │                                  │ Store Cached Response            │
  │                                  │ Update Status to COMPLETED       │
  │                                  │ TTL: 24 Hours                    │
  │                                  │                                  │
  │◄─────────────────────────────────┴──────────────────────────────────┤
  │ 200 OK (Processed Result)
```

### 10.2 Persistence Model
* **Database Entity (`idempotency_records`)**:
  * `idempotency_key` (VARCHAR(128), PRIMARY KEY)
  * `request_hash` (SHA-256 hash of payload to prevent key reuse with altered payloads)
  * `payment_id` (UUID foreign reference)
  * `response_json` (TEXT containing cached API response)
  * `status` (`IN_PROGRESS`, `COMPLETED`, `FAILED`)
  * `created_at`, `expires_at` (Default TTL: 1440 minutes / 24 hours)

---

## 11. Double-Entry Accounting Engine & Ledger

Balances are never stored as arbitrary, unchecked numbers. Every balance change is backed by an auditable journal of immutable debit and credit entries.

### 11.1 The Golden Accounting Equation
For any given financial transaction $T$:
$$\sum \text{Debits}(T) + \sum \text{Credits}(T) = 0$$

Where:
* **Asset / Expense Accounts**: Debits increase balance (+), Credits decrease balance (-).
* **Liability / Equity Accounts**: Credits increase balance (+), Debits decrease balance (-).

### 11.2 Standard System Accounts
1. `SYSTEM_CLEARING_TELEBIRR`: External clearing account representing funds held at Ethio Telecom.
2. `CUSTOMER_WALLET_{id}`: Customer internal liability account representing funds owed to the user.
3. `SYSTEM_FEE_REVENUE`: Revenue account capturing platform service charges.
4. `SYSTEM_ESCROW_HOLD`: Intermediate hold account during pending withdrawals or conditional transfers.

### 11.3 Ledger Entry Mapping Examples

#### A. Top-Up Settlement (1,000 ETB)
| Account | Entry Type | Amount | Running Balance Impact |
|---|---|---|---|
| `SYSTEM_CLEARING_TELEBIRR` | `DEBIT` | 1,000 ETB | Ethio Telecom clearing receivable increases |
| `CUSTOMER_WALLET_123` | `CREDIT` | 1,000 ETB | Customer wallet liability increases |

#### B. Withdrawal Cash-Out (500 ETB)
| Account | Entry Type | Amount | Running Balance Impact |
|---|---|---|---|
| `CUSTOMER_WALLET_123` | `DEBIT` | 500 ETB | Customer wallet balance decreases |
| `SYSTEM_CLEARING_TELEBIRR` | `CREDIT` | 500 ETB | Ethio Telecom clearing asset decreases |

#### C. Peer-to-Peer Transfer (200 ETB)
| Account | Entry Type | Amount | Running Balance Impact |
|---|---|---|---|
| `CUSTOMER_WALLET_A` | `DEBIT` | 200 ETB | Sender balance decreases |
| `CUSTOMER_WALLET_B` | `CREDIT` | 200 ETB | Receiver balance increases |

---

## 12. Concurrency Control & Double-Spend Prevention

To support multi-threaded concurrent requests and multi-instance container deployments:

### 12.1 Pessimistic Account Balance Locking
When funds are debited from a wallet (e.g. withdrawal or transfer):
```sql
SELECT * FROM ledger_accounts WHERE id = :accountId FOR UPDATE;
```
This forces concurrent debit requests on the same account to serialize, eliminating race conditions and overdrafts.

### 12.2 Optimistic Aggregate Locking
The `PaymentEntity` includes a JPA `@Version` column:
* If two threads attempt to process a callback or status update simultaneously, the second transaction fails with `OptimisticLockingFailureException` and is safely rolled back.

### 12.3 Unique Reference Constraints
Database-enforced unique constraints on `payments.reference` and `payment_attempts.provider_reference` physically prevent duplicate entries at the database storage engine layer.

---

## 13. Database Relational Schema & DDL

The database is PostgreSQL 16+. Below is the concrete relational DDL:

```sql
-- 1. Payments Table (Aggregate Root)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    customer_id UUID NOT NULL,
    source_account_id UUID,
    destination_account_id UUID,
    provider VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(128),
    source_system VARCHAR(64),
    description VARCHAR(255),
    metadata JSONB,
    failure_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_reference ON payments(reference);
CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- 2. Transactions Table
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    reference VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    fee NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    account_id UUID NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_payment_id ON transactions(payment_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_reference ON transactions(reference);

-- 3. Payment Attempts (Provider Interaction Audit)
CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_id VARCHAR(128),
    provider_reference VARCHAR(128),
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts(payment_id);
CREATE INDEX idx_payment_attempts_provider_ref ON payment_attempts(provider_reference);

-- 4. Double-Entry Ledger Entries
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL,
    entry_type VARCHAR(16) NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    running_balance NUMERIC(19, 4) NOT NULL,
    reference VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);

-- 5. Idempotency Records
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    payment_id UUID REFERENCES payments(id),
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_records(expires_at);
```

---

## 14. REST API Specification

### 14.1 Standard Response Envelope
All REST endpoints return the standard `ApiResponse<T>` wrapper:
```json
{
    "success": true,
    "message": "Operation completed successfully",
    "data": { ... },
    "error": null,
    "correlationId": "8f4a1c2d-9876-4321-ba98-123456789abc",
    "timestamp": "2026-09-25T16:00:00.000Z"
}
```

### 14.2 Endpoints Summary

| Method | Endpoint | Description | Key Headers |
|---|---|---|---|
| `POST` | `/api/v1/payments/topups` | Initiate C2B top-up | `Idempotency-Key` (Required) |
| `POST` | `/api/v1/payments/withdrawals` | Initiate B2C withdrawal | `Idempotency-Key` (Required) |
| `POST` | `/api/v1/payments/transfers` | Initiate P2P/ledger transfer | `Idempotency-Key` (Required) |
| `GET` | `/api/v1/payments/{paymentId}` | Get payment status & details | `X-Correlation-ID` |
| `GET` | `/api/v1/payments/reference/{ref}` | Get payment by reference | `X-Correlation-ID` |
| `GET` | `/api/v1/payments` | Search payments (paginated) | `X-Correlation-ID` |
| `POST` | `/api/v1/payment-providers/telebirr/callback` | Telebirr asynchronous webhook | Provider signature |

### 14.3 Request & Response Examples

#### A. Top-Up Request
```http
POST /api/v1/payments/topups HTTP/1.1
Host: api.naditech.com
Content-Type: application/json
Idempotency-Key: topup-user123-20260925-001
X-Correlation-ID: corr-7c9d0e1f

{
    "amount": 1500.00,
    "currency": "ETB",
    "customerId": "d3b07384-d113-469b-a010-85f269a23d8c",
    "paymentMethod": {
        "type": "TELEBIRR",
        "identifier": "0911223344"
    },
    "description": "App Wallet Top-Up",
    "sourceSystem": "MOBILE_APP"
}
```

#### Response (200 OK):
```json
{
    "success": true,
    "message": "Top-up initiated successfully",
    "data": {
        "id": "e4a2f8b1-38f1-460d-9b7e-908bc43d1a8e",
        "reference": "TOP-20260925-9B7E90",
        "type": "TOP_UP",
        "status": "PENDING",
        "amount": 1500.00,
        "currency": "ETB",
        "customerId": "d3b07384-d113-469b-a010-85f269a23d8c",
        "provider": "TELEBIRR",
        "checkoutUrl": "https://app.ethiomobilemoney.et:2121/payment/web/pay?params=...",
        "createdAt": "2026-09-25T16:05:00.000Z"
    },
    "error": null,
    "correlationId": "corr-7c9d0e1f",
    "timestamp": "2026-09-25T16:05:00.120Z"
}
```

#### B. Withdrawal Request
```http
POST /api/v1/payments/withdrawals HTTP/1.1
Host: api.naditech.com
Content-Type: application/json
Idempotency-Key: wth-user123-20260925-002
X-Correlation-ID: corr-8a1b2c3d

{
    "amount": 500.00,
    "currency": "ETB",
    "customerId": "d3b07384-d113-469b-a010-85f269a23d8c",
    "sourceAccountId": "b2c1d0e9-f8a7-4b6c-9d8e-1a2b3c4d5e6f",
    "paymentMethod": {
        "type": "TELEBIRR",
        "identifier": "0911223344"
    },
    "description": "Payout to Telebirr Wallet"
}
```

#### C. Standard Error Response
```json
{
    "success": false,
    "message": "Account has insufficient balance for withdrawal of 500.00 ETB",
    "data": null,
    "error": {
        "code": "INSUFFICIENT_FUNDS",
        "details": "Available balance: 120.00 ETB. Requested: 500.00 ETB",
        "fieldErrors": []
    },
    "correlationId": "corr-8a1b2c3d",
    "timestamp": "2026-09-25T16:06:00.050Z"
}
```

---

## 15. Reconciliation & In-Doubt Transaction Resolution

In real-world networks, external payment webhooks can be lost, delayed by firewalls, or interrupted by client disconnections.

```text
               ┌────────────────────────────────────────────────────────┐
               │    Scheduled Polling Worker (Every 5 Minutes)          │
               └───────────────────────────┬────────────────────────────┘
                                           │
                                           ▼
                     Query `payments` WHERE status = 'PENDING'
                     AND created_at < NOW() - INTERVAL '3 MINUTES'
                                           │
                         ┌─────────────────┴─────────────────┐
                         │ Loop over in-doubt payments       │
                         └─────────────────┬─────────────────┘
                                           │
                                           ▼
                           PaymentProvider.checkStatus(ref)
                                           │
                 ┌─────────────────────────┼─────────────────────────┐
                 ▼                         ▼                         ▼
          Provider: SUCCESS         Provider: FAILED          Provider: NOT FOUND /
                 │                         │                  STILL PENDING
                 ▼                         ▼                         │
         Apply Callback Logic      Mark Payment FAILED               ▼
         - Credit Wallet           - Revert Held Funds        If created_at > 30m:
         - Settle Ledger           - Publish FailedEvent        Mark EXPIRED
         - Mark SUCCESS                                       Else:
                                                                Retry next tick
```

### 15.1 End-of-Day (EOD) Batch Reconciliation
* The system supports automated reconciliation against Telebirr's daily transaction settlement statement (CSV/SFTP).
* Discrepancies generate automated ledger adjustment alerts and dispute tickets.

---

## 16. Event-Driven Architecture & Transactional Outbox

### 16.1 Domain Event Catalog

| Event Name | Topic / Routing Key | Payload Attributes | Emitted When |
|---|---|---|---|
| `PaymentInitiatedEvent` | `payment.events.initiated` | `paymentId`, `reference`, `amount`, `currency`, `customerId`, `provider` | Payment created & provider contacted |
| `PaymentCompletedEvent` | `payment.events.completed` | `paymentId`, `reference`, `amount`, `currency`, `customerId`, `providerRef` | Payment successfully settled & ledger booked |
| `PaymentFailedEvent` | `payment.events.failed` | `paymentId`, `reference`, `amount`, `currency`, `customerId`, `reason` | Payment rejected or aborted |
| `TransferCompletedEvent` | `transfer.events.completed` | `paymentId`, `reference`, `amount`, `sourceAccountId`, `destAccountId` | Atomic transfer finalized |

### 16.2 Transactional Outbox Pattern (Zero Dual-Write Hazard)
To prevent dual-write anomalies (e.g., database commit succeeds but Kafka broker is down):
1. Domain events are inserted into an `outbox_events` table inside the **same database transaction** that updates the `payments` and `ledger_entries` tables.
2. A lightweight background CDC worker (Debezium or Spring Polling Publisher) tails the outbox table and streams events to Kafka with guaranteed at-least-once delivery.

---

## 17. Reliability, Resilience & Fault Tolerance

```text
 Incoming Request ──► [Rate Limiter] ──► [Retry Mechanism] ──► [Circuit Breaker] ──► Telebirr Gateway
                                              (Max 3)           (Resilience4j)
```

### 17.1 Resilience4j Configuration Matrix

```yaml
resilience4j:
  circuitbreaker:
    instances:
      telebirrProvider:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50.0
        slow-call-rate-threshold: 75.0
        slow-call-duration-threshold: 5000ms
        wait-duration-in-open-state: 15000ms
        permitted-number-of-calls-in-half-open-state: 5
  retry:
    instances:
      telebirrProvider:
        max-attempts: 3
        wait-duration: 1000ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException
          - org.springframework.web.reactive.function.client.WebClientRequestException
        ignore-exceptions:
          - com.payment.nadi.domain.exception.InvalidPaymentException
          - com.payment.nadi.domain.exception.InsufficientBalanceException
```

* **Non-Retryable Exceptions**: Business validation errors, insufficient funds, signature mismatches, or invalid accounts are never retried.
* **Safe Retries**: Only idempotent queries or transient network timeouts trigger exponential retries.

---

## 18. Security Architecture & Threat Model

### 18.1 Defensive Principles
1. **Network Boundary**: The Payment Service is deployed within a private VPC subnet. Public internet traffic hits the API Gateway; only the Gateway and Telebirr callback webhook can reach the service.
2. **Zero-Trust Token Validation**: Requests are authenticated via JWT bearer tokens validated against the Identity Provider public key (RS256).
3. **Log Sanitization & Data Masking**:
   * All customer phone numbers are masked in logs (e.g., `09****1234`).
   * Passwords, private keys, client secrets, and tokens are scrubbed via custom log filters.
4. **Anti-Tampering / Webhook Replay Protection**:
   * Telebirr callbacks require valid RSA signature verification.
   * Callback timestamps must fall within an acceptable clock skew window (e.g. 5 minutes).

---

## 19. Observability & Distributed Tracing

Every transaction lifecycle is observable end-to-end:

### 19.1 Correlation ID Tracing
* Handled by `CorrelationIdFilter`.
* Checks for incoming `X-Correlation-ID` header; if missing, generates a new UUID.
* Propagates correlation ID into SLF4J / Logback **Mapped Diagnostic Context (MDC)**.
* Forwards the header on all outbound HTTP WebClient calls to Telebirr and attaches it to Kafka message headers.

### 19.2 Key Metrics (Micrometer / Prometheus)

| Metric Name | Type | Description |
|---|---|---|
| `payment_initiated_total` | Counter | Total payments initiated, tagged by `type` and `provider` |
| `payment_completed_total` | Counter | Total successful payments, tagged by `type` |
| `payment_failed_total` | Counter | Total failed payments, tagged by `error_code` |
| `payment_duration_seconds` | Timer | End-to-end processing latency histogram |
| `provider_request_duration_seconds` | Timer | Upstream gateway HTTP latency (Telebirr) |
| `ledger_discrepancy_count` | Counter | Critical alert: must remain 0 at all times |

---

## 20. Configuration Specification

Application configurations are centralized in `application.yml` and bound via typed properties:

```yaml
server:
  port: 8080

spring:
  application:
    name: payment-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/mydatabase}
    username: ${SPRING_DATASOURCE_USERNAME:myuser}
    password: ${SPRING_DATASOURCE_PASSWORD:secret}
    hikari:
      maximum-pool-size: 10
      connection-timeout: 20000

payment:
  default-currency: ETB
  idempotency:
    ttl-minutes: 1440
  providers:
    telebirr:
      enabled: true
      base-url: ${TELEBIRR_BASE_URL:https://app.ethiomobilemoney.et:2121}
      app-id: ${TELEBIRR_APP_ID}
      app-key: ${TELEBIRR_APP_KEY}
      short-code: ${TELEBIRR_SHORT_CODE}
      public-key: ${TELEBIRR_PUBLIC_KEY}
      private-key: ${TELEBIRR_PRIVATE_KEY}
      callback-url: ${TELEBIRR_CALLBACK_URL:http://localhost:8080/api/v1/payment-providers/telebirr/callback}
      return-url: ${TELEBIRR_RETURN_URL:http://localhost:8080/api/v1/payments/return}
      timeout-seconds: 15
```

---

## 21. Testing Strategy

The test suite ensures verification across all layers of the architecture:

```text
                      ┌───────────────────────┐
                      │      End-to-End       │  (5%)  Testcontainers + Full Spring Context
                      ├───────────────────────┤
                      │   Integration Tests   │  (25%) Controller + Repository + WireMock
                      ├───────────────────────┤
                      │  Application UseCases │  (30%) Mocked Ports & Business Rules
                      ├───────────────────────┤
                      │   Pure Domain Unit    │  (40%) Money, Payment Aggregate, Ledger
                      └───────────────────────┘
```

1. **Domain Unit Tests**: Test entity invariants, monetary math, rounding rules, and state machine transitions in sub-millisecond execution time without Spring.
2. **Application UseCase Tests**: Verify idempotency caching, double-entry ledger postings, and error reversals using Mockito.
3. **WireMock Provider Tests**: Mock external Telebirr C2B/B2C endpoints to test timeouts, malformed signatures, gateway 500 errors, and recovery.
4. **Testcontainers PostgreSQL Tests**: Validate actual SQL DDL, indexes, optimistic locking `@Version` conflicts, and foreign key constraints against a real PostgreSQL instance.

---

## 22. Architectural Adherence Checklist

Before merging any new feature or payment provider adapter, verify against this checklist:

* [ ] **Domain Purity**: Domain model imports zero Spring, JPA, or HTTP classes.
* [ ] **Vendor Isolation**: No Telebirr DTO, constant, or SDK class exists outside `infrastructure/provider/telebirr/`.
* [ ] **Double-Entry Balance**: Every balance adjustment has corresponding debit and credit ledger entries summing to zero.
* [ ] **Strict Monetary Types**: All financial values use the `Money` record with `BigDecimal` and `Currency`.
* [ ] **Two-Phase Idempotency**: All mutating operations (`topup`, `withdraw`, `transfer`) mandate and enforce `Idempotency-Key`.
* [ ] **Safe Concurrency**: Balance withdrawals apply pessimistic locks (`FOR UPDATE`) on account records.
* [ ] **Cryptographic Verification**: Webhooks verify digital signatures using the provider's public key before touching business logic.
* [ ] **Zero-Leak Logging**: Sensitive customer data and secrets are masked in application logs.
