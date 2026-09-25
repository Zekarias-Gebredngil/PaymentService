package com.payment.nadi.domain.model;

import com.payment.nadi.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment Aggregate Root representing a financial movement.
 * Encapsulates core payment invariants and lifecycle transitions.
 */
public class Payment {

    private UUID id;
    private String reference;
    private PaymentType type;
    private PaymentStatus status;
    private Money amount;
    private UUID customerId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private PaymentProviderType provider;
    private String providerReference;
    private String sourceSystem;
    private String description;
    private Map<String, String> metadata;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public Payment() {
        this.metadata = new HashMap<>();
    }

    public static Payment create(
            String reference,
            PaymentType type,
            Money amount,
            UUID customerId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            PaymentProviderType provider,
            String sourceSystem,
            String description,
            Map<String, String> metadata
    ) {
        Objects.requireNonNull(reference, "Payment reference is required");
        Objects.requireNonNull(type, "Payment type is required");
        Objects.requireNonNull(amount, "Payment amount is required");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.reference = reference;
        payment.type = type;
        payment.status = PaymentStatus.CREATED;
        payment.amount = amount;
        payment.customerId = customerId;
        payment.sourceAccountId = sourceAccountId;
        payment.destinationAccountId = destinationAccountId;
        payment.provider = provider;
        payment.sourceSystem = (sourceSystem != null && !sourceSystem.isBlank()) ? sourceSystem : "DEFAULT";
        payment.description = description;
        payment.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        Instant now = Instant.now();
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void markAsPending(String providerRef) {
        transitionTo(PaymentStatus.PENDING);
        if (providerRef != null && !providerRef.isBlank()) {
            this.providerReference = providerRef;
        }
    }

    public void markAsProcessing() {
        transitionTo(PaymentStatus.PROCESSING);
    }

    public void markAsSuccess(String providerRef) {
        transitionTo(PaymentStatus.SUCCESS);
        if (providerRef != null && !providerRef.isBlank()) {
            this.providerReference = providerRef;
        }
    }

    public void markAsFailed(String reason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    public void markAsCancelled(String reason) {
        transitionTo(PaymentStatus.CANCELLED);
        this.failureReason = reason;
    }

    public void markAsExpired() {
        transitionTo(PaymentStatus.EXPIRED);
    }

    private void transitionTo(PaymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException(this.status, newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters for reconstruction
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public PaymentType getType() { return type; }
    public void setType(PaymentType type) { this.type = type; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public UUID getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public UUID getDestinationAccountId() { return destinationAccountId; }
    public void setDestinationAccountId(UUID destinationAccountId) { this.destinationAccountId = destinationAccountId; }

    public PaymentProviderType getProvider() { return provider; }
    public void setProvider(PaymentProviderType provider) { this.provider = provider; }

    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>(); }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
