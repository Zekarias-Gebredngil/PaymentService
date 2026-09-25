package com.payment.nadi.application.port.out.dto;

import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentStatus;

/**
 * Result returned when querying transaction status from a provider.
 */
public record PaymentStatusResult(
        PaymentStatus status,
        String providerReference,
        Money amount,
        String errorCode,
        String errorMessage,
        String rawResponse
) {
    public static PaymentStatusResult success(String providerReference, Money amount, String raw) {
        return new PaymentStatusResult(PaymentStatus.SUCCESS, providerReference, amount, null, null, raw);
    }

    public static PaymentStatusResult pending(String providerReference, Money amount, String raw) {
        return new PaymentStatusResult(PaymentStatus.PENDING, providerReference, amount, null, null, raw);
    }

    public static PaymentStatusResult failed(String providerReference, String errorCode, String errorMessage, String raw) {
        return new PaymentStatusResult(PaymentStatus.FAILED, providerReference, null, errorCode, errorMessage, raw);
    }
}
