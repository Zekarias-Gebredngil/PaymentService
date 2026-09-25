package com.payment.nadi.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity recording every interaction attempt made to an external provider.
 * Facilitates complete provider interaction auditing and debugging.
 */
public class PaymentAttempt {

    private UUID id;
    private UUID paymentId;
    private PaymentProviderType provider;
    private int attemptNumber;
    private String status;
    private String requestId;
    private String providerReference;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;

    public PaymentAttempt() {
    }

    public static PaymentAttempt record(
            UUID paymentId,
            PaymentProviderType provider,
            int attemptNumber,
            String status,
            String requestId,
            String providerReference,
            String errorCode,
            String errorMessage
    ) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.id = UUID.randomUUID();
        attempt.paymentId = paymentId;
        attempt.provider = provider;
        attempt.attemptNumber = attemptNumber;
        attempt.status = status;
        attempt.requestId = requestId;
        attempt.providerReference = providerReference;
        attempt.errorCode = errorCode;
        attempt.errorMessage = errorMessage;
        attempt.createdAt = Instant.now();
        return attempt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }

    public PaymentProviderType getProvider() { return provider; }
    public void setProvider(PaymentProviderType provider) { this.provider = provider; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
