package com.payment.nadi.application.port.out.dto;

import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentType;

import java.util.Map;
import java.util.UUID;

/**
 * Generic agnostic request passed to payment providers.
 * Isolates core domain from provider-specific parameters.
 */
public record PaymentProviderRequest(
        UUID paymentId,
        String reference,
        PaymentType paymentType,
        Money amount,
        UUID customerId,
        String customerPhone,
        String customerEmail,
        String description,
        String returnUrl,
        String callbackUrl,
        Map<String, String> extraParams
) {
    public static PaymentProviderRequest of(
            UUID paymentId,
            String reference,
            PaymentType paymentType,
            Money amount,
            UUID customerId,
            String customerPhone,
            String description,
            String returnUrl,
            String callbackUrl
    ) {
        return new PaymentProviderRequest(
                paymentId,
                reference,
                paymentType,
                amount,
                customerId,
                customerPhone,
                null,
                description,
                returnUrl,
                callbackUrl,
                Map.of()
        );
    }
}
