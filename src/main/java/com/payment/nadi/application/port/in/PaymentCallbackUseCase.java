package com.payment.nadi.application.port.in;

import com.payment.nadi.domain.model.Money;
import com.payment.nadi.domain.model.PaymentProviderType;
import com.payment.nadi.domain.model.PaymentStatus;

public interface PaymentCallbackUseCase {

    record CallbackResult(
            boolean accepted,
            String message,
            String paymentReference,
            PaymentStatus finalStatus
    ) {}

    CallbackResult processCallback(
            PaymentProviderType providerType,
            String paymentReference,
            String providerReference,
            PaymentStatus reportedStatus,
            Money reportedAmount,
            String rawPayload,
            boolean signatureVerified
    );
}
