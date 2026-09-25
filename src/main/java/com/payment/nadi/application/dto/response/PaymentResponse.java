package com.payment.nadi.application.dto.response;

import com.payment.nadi.domain.model.Payment;
import com.payment.nadi.domain.model.PaymentStatus;
import com.payment.nadi.domain.model.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String reference,
        PaymentType type,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        UUID customerId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String provider,
        String providerReference,
        String redirectUrl,
        String failureReason,
        String sourceSystem,
        Map<String, String> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return from(payment, null);
    }

    public static PaymentResponse from(Payment payment, String redirectUrl) {
        return new PaymentResponse(
                payment.getId(),
                payment.getReference(),
                payment.getType(),
                payment.getStatus(),
                payment.getAmount().amount(),
                payment.getAmount().getCurrencyCode(),
                payment.getCustomerId(),
                payment.getSourceAccountId(),
                payment.getDestinationAccountId(),
                payment.getProvider() != null ? payment.getProvider().name() : null,
                payment.getProviderReference(),
                redirectUrl,
                payment.getFailureReason(),
                payment.getSourceSystem(),
                payment.getMetadata(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
