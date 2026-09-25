package com.payment.nadi.application.port.out;

import com.payment.nadi.application.port.out.dto.PaymentInitiationResult;
import com.payment.nadi.application.port.out.dto.PaymentProviderRequest;
import com.payment.nadi.application.port.out.dto.PaymentStatusResult;
import com.payment.nadi.domain.model.PaymentProviderType;

/**
 * Port representing an external payment provider gateway (Telebirr, Chapa, CBE Birr, etc.).
 * Implementation resides exclusively in infrastructure.
 */
public interface PaymentProvider {

    PaymentProviderType getProvider();

    PaymentInitiationResult initiateTopUp(PaymentProviderRequest request);

    PaymentInitiationResult initiateWithdraw(PaymentProviderRequest request);

    PaymentInitiationResult initiateTransfer(PaymentProviderRequest request);

    PaymentStatusResult checkStatus(String providerReference);
}
