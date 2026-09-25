package com.payment.nadi.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing an executed financial transaction record.
 */
public class Transaction {

    private UUID id;
    private UUID paymentId;
    private String transactionReference;
    private PaymentType type;
    private Money amount;
    private Money fee;
    private UUID accountId;
    private String description;
    private PaymentStatus status;
    private Instant createdAt;

    public Transaction() {
    }

    public static Transaction create(
            UUID paymentId,
            String transactionReference,
            PaymentType type,
            Money amount,
            Money fee,
            UUID accountId,
            String description,
            PaymentStatus status
    ) {
        Transaction tx = new Transaction();
        tx.id = UUID.randomUUID();
        tx.paymentId = Objects.requireNonNull(paymentId, "Payment ID cannot be null");
        tx.transactionReference = Objects.requireNonNull(transactionReference, "Transaction reference cannot be null");
        tx.type = Objects.requireNonNull(type, "Payment type cannot be null");
        tx.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        tx.fee = fee != null ? fee : Money.zero(amount.currency());
        tx.accountId = accountId;
        tx.description = description;
        tx.status = status != null ? status : PaymentStatus.PENDING;
        tx.createdAt = Instant.now();
        return tx;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public PaymentType getType() { return type; }
    public void setType(PaymentType type) { this.type = type; }

    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }

    public Money getFee() { return fee; }
    public void setFee(Money fee) { this.fee = fee; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
