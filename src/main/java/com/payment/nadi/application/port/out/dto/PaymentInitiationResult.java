package com.payment.nadi.application.port.out.dto;

import com.payment.nadi.domain.model.PaymentStatus;

import java.util.Map;

/**
 * Standardized result returned when a provider initiates an operation.
 */
public record PaymentInitiationResult(
        boolean success,
        PaymentStatus status,
        String providerReference,
        String redirectUrl,
        String rawResponse,
        String errorCode,
        String errorMessage,
        Map<String, String> additionalData
) {
    public static PaymentInitiationResult pending(String providerReference, String redirectUrl, String rawResponse) {
        return new PaymentInitiationResult(
                true,
                PaymentStatus.PENDING,
                providerReference,
                redirectUrl,
                rawResponse,
                null,
                null,
                Map.of()
        );
    }

    public static PaymentInitiationResult success(String providerReference, String rawResponse) {
        return new PaymentInitiationResult(
                true,
                PaymentStatus.SUCCESS,
                providerReference,
                null,
                rawResponse,
                null,
                null,
                Map.of()
        );
    }

    public static PaymentInitiationResult failed(String errorCode, String errorMessage, String rawResponse) {
        return new PaymentInitiationResult(
                false,
                PaymentStatus.FAILED,
                null,
                null,
                rawResponse,
                errorCode,
                errorMessage,
                Map.of()
        );
    }
}
